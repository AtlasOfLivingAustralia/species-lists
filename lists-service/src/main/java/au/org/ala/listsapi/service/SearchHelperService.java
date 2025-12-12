/*
 * Copyright (C) 2025 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.listsapi.service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.mongodb.bulk.BulkWriteResult;

import au.org.ala.listsapi.controller.AuthUtils;
import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.FacetCount;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.util.ElasticUtils;
import au.org.ala.ws.security.profile.AlaUserProfile;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketBase;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;

/**
 * A helper service for performing search-related operations on species lists.
 * Provides read and write operations for species list items,
 * interacting with both MongoDB and Elasticsearch.
 */
@Service
public class SearchHelperService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SearchHelperService.class);
    @Autowired private AuthUtils authUtils;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ElasticsearchOperations elasticsearchOperations;
    @Autowired private SpeciesListMongoRepository speciesListMongoRepository;

    private static final String SPECIES_LIST_ID = "speciesListID";
    private static final int MAX_LIST_ENTRIES = 10000;

    private static final Set<String> CORE_FIELDS = Set.of(
                    "id",
                    "scientificName",
                    "vernacularName",
                    "licence",
                    "taxonID",
                    "kingdom",
                    "phylum",
                    "class",
                    "order",
                    "family",
                    "genus",
                    "isBIE",
                    "listType",
                    "isAuthoritative",
                    "hasRegion",
                    "isSDS",
                    "isThreatened",
                    "isInvasive",
                    "isPrivate",
                    "tags");


    /**
     * Performs a bulk update on a list of SpeciesListItem objects.
     *
     * @param items
     * @param keys
     * @return BulkWriteResult result of the operation
     */
    public BulkWriteResult speciesListItemsBulkUpdate(List<SpeciesListItem> items, List<String> keys) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, SpeciesListItem.class);
        for (SpeciesListItem item : items) {
            // Build an upsert or replace operation based on unique identifier
            Query query = new Query(Criteria.where("_id").is(item.getId()));
            Update update = new Update();
            keys.forEach(key -> update.set(key, item.getPropFromKey(key)));

            bulkOps.upsert(query, update);
        }

        // Execute the bulk operation
        return bulkOps.execute();
    }

    /**
     * Performs a bulk save on a list of SpeciesListItem objects.
     *
     * @param items
     * @return BulkWriteResult result of the operation
     */
    public BulkWriteResult speciesListItemsBulkSave(List<SpeciesListItem> items) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, SpeciesListItem.class);
        bulkOps.insert(items);

        // Execute the bulk operation
        return bulkOps.execute();
    }

    /**
     * Fetches species list items based on GUIDs and optional species list IDs.
     * Supports pagination and filtering pof public/private lists based on user roles.
     *
     * @param guids
     * @param speciesListIDs
     * @param page
     * @param pageSize
     * @param principal
     * @return List<SpeciesListItem> of species list items
     */
    public List<SpeciesListItem> fetchSpeciesListItems(
            String guids,
            @Nullable String speciesListIDs,
            int page,
            int pageSize,
            Principal principal
    ) {
        AlaUserProfile profile = authUtils.getUserProfile(principal);
        List<FieldValue> GUIDs = Arrays.stream(guids.split(",")).map(FieldValue::of).toList();
        List<FieldValue> listIDs = speciesListIDs != null ?
                Arrays.stream(speciesListIDs.split(",")).map(FieldValue::of).toList() : null;

        if (page < 1 || (page * pageSize) > 10000) {
            return new ArrayList<>();
        }

        Pageable pageableRequest = PageRequest.of(page - 1, pageSize);
        NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
        builder.withQuery(
                q ->
                        q.bool(
                                bq -> {
                                    bq.filter(f -> f.terms(t -> t.field("classification.taxonConceptID.keyword")
                                            .terms(ta -> ta.value(GUIDs))));

                                    if (listIDs != null) {
                                        bq.filter(f -> f.bool(b -> b
                                                .should(s -> s.terms(t -> t.field("speciesListID.keyword").terms(ta -> ta.value(listIDs))))
                                                .should(s -> s.terms(t -> t.field("dataResourceUid.keyword").terms(ta -> ta.value(listIDs))))
                                        ));
                                    }

                                    // If the user is not an admin or doesn't have internal scope, only query their private lists, and all other public lists
                                    if (!authUtils.isAuthenticated(principal)) {
                                        logger.debug("Filtering for public lists only (user not authenticated)");
                                        bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
                                    } else if (!authUtils.hasAdminRole(profile) && !authUtils.hasInternalScope(profile)) {
                                        logger.debug("Filtering for private lists only (non-admin/non-internal users)");
                                        bq.filter(f -> f.bool(b -> b
                                                .should(s -> s.bool(b2 -> b2
                                                        // .must(m -> m.term(t -> t.field("owner").value(profile.getUserId())))
                                                        .must(m -> m.term(t -> t.field("isPrivate").value(true)))
                                                ))
                                                .should(s -> s.term(t -> t.field("isPrivate").value(false)))
                                        ));
                                    }
                                    // If user is admin or has internal scope, no filters applied (can see all lists)

                                    return bq;
                                }));

        NativeQuery query = builder.build();
        query.setPageable(pageableRequest);
        SearchHits<SpeciesListIndex> results =
                elasticsearchOperations.search(
                        query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

        return ElasticUtils.convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));
    }

    /**
     * Fetches species list items based on species list IDs and optional search query
     * that can be restricted to specific fields.
     * Supports pagination and filtering of public/private lists based on user roles.
     *
     * @param speciesListIDs
     * @param searchQuery
     * @param fields
     * @param page
     * @param pageSize
     * @param sort
     * @param dir
     * @param principal
     * @return List<SpeciesListItem> of species list items
     */
    public List<SpeciesListItem> fetchSpeciesListItems(
            String speciesListIDs,
            @Nullable String searchQuery,
            @Nullable String fields,
            @Nullable Integer page,
            @Nullable Integer pageSize,
            @Nullable String sort,
            @Nullable String dir,
            Principal principal) throws IllegalArgumentException {
        List<String> IDs = Arrays.stream(speciesListIDs.split(",")).toList();
        List<SpeciesList> foundLists = speciesListMongoRepository.findAllByDataResourceUidIsInOrIdIsIn(IDs, IDs);
        HashSet<String> restrictedFields = new HashSet<>();

        if (fields != null && !fields.isBlank()) {
            restrictedFields.addAll(Arrays.stream(fields.split(",")).collect(Collectors.toSet()));
        }

        if (!foundLists.isEmpty()) {
            List<FieldValue> validIDs = foundLists.stream()
                    .filter(list -> !list.getIsPrivate() || authUtils.isAuthorized(list, principal))
                    .map(list -> FieldValue.of(list.getId())).toList();

            // Enforce ElasticSearch limit of 10,000 documents
            if ((page - 1) * pageSize + pageSize > 10000 && pageSize > 0) {
                // throw new IllegalArgumentException("Page size exceeds ElasticSearch limit of 10,000 documents.");
                // Reverted to returning empty list to avoid breaking existing clients (biocache-service, etc)
                return new ArrayList<>();
            } else if ((page - 1) * pageSize + pageSize > 10000) {
                throw new IllegalArgumentException("Page size exceeds ElasticSearch limit of 10,000 documents.");
            }
            
            if (page < 1 || validIDs.isEmpty()) {
                return new ArrayList<>();
            }

            Boolean isAdmin = principal != null ? (authUtils.hasAdminRole(authUtils.getUserProfile(principal)) || authUtils.hasInternalScope(authUtils.getUserProfile(principal))) : false;
            ArrayList<Filter> tempFilters = new ArrayList<>();

            String sortField = (sort != null && !sort.isBlank()) ? sort : "scientificName";
            String sortDir = (dir != null && !dir.isBlank()) ? dir : "asc";
            
            // Create Spring Data Sort
            Sort springSort = 
                Sort.by(
                    "asc".equalsIgnoreCase(sortDir) 
                        ? Sort.Direction.ASC 
                        : Sort.Direction.DESC,
                    sortField
                );

            // Create Pageable with Sort
            Pageable pageableRequest = PageRequest.of(page - 1, pageSize, springSort);
            NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
            builder.withQuery(
                    q -> q.bool(
                            bq -> {
                                ElasticUtils.buildQuery(ElasticUtils.cleanRawQuery(searchQuery), validIDs, null, isAdmin, null, tempFilters, bq);
                                ElasticUtils.restrictFields(searchQuery, restrictedFields, bq);
                                return bq;
                            }));

            NativeQuery query = builder.build();
            SearchHits<SpeciesListIndex> results =
                    elasticsearchOperations.search(
                            query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));
                    
            if (!results.isEmpty()) {
                SpeciesListIndex firstItem = results.getSearchHit(0).getContent();
                logger.debug("First item: " + firstItem);
            }

            List<SpeciesListItem> speciesListItems =
                    ElasticUtils.convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));

            return speciesListItems;
        }

        return new ArrayList<>();
    }

    /**
     * Finds common keys across multiple SpeciesList objects.
     * This method is useful for identifying shared attributes
     *
     * @param lists - List of SpeciesList objects
     * @return Set<String> of common keys
     */
    public static Set<String> findCommonKeys(List<SpeciesList> lists) {
        // Handle edge cases
        if (lists == null || lists.isEmpty()) {
            return Collections.emptySet();
        }

        // If there is only one list, its contents are trivially the common elements
        if (lists.size() == 1) {
            return new HashSet<>(lists.get(0).getFieldList());
        }

        // Sort lists by size (smallest first) to optimize intersection performance
        lists.sort(Comparator.comparingInt(l -> l.getFieldList().size()));

        // Initialize 'common' with the first (smallest) list
        Set<String> common = new HashSet<>(lists.get(0).getFieldList());

        // Intersect with each subsequent list
        for (int i = 1; i < lists.size(); i++) {
            common.retainAll(lists.get(i).getFieldList());
            // If at any point the set becomes empty, we can stop
            if (common.isEmpty()) {
                break;
            }
        }

        return common;
    }

    /**
     * Searches SpeciesList documents based on various criteria including user access rights.
     * Documents can be filtered by attributes such as isAuthoritative, isThreatened, and isInvasive.
     * The search also respects user roles, ensuring that private lists are only visible to their owners
     * or to users with admin privileges.
     * @param speciesListQuery
     * @param userId
     * @param isAdmin
     * @param searchTerm
     * @param pageable
     * @return
     */
    public Page<SpeciesList> searchDocuments(SpeciesList speciesListQuery, String userId, Boolean isAdmin, String searchTerm, Pageable pageable) {
        // Your search criteria
        Criteria searchCriteria = new Criteria().orOperator(
            Criteria.where("title").regex(searchTerm, "i"),
            Criteria.where("description").regex(searchTerm, "i")
        );
        
        // Build query with access control
        Criteria finalCriteria = buildDocumentAccessCriteria(userId, isAdmin, searchCriteria);
        Query query = new Query(finalCriteria);

        if (speciesListQuery.getIsAuthoritative() != null) {
            query.addCriteria(Criteria.where("isAuthoritative").is(speciesListQuery.getIsAuthoritative()));
        }
        if (speciesListQuery.getIsThreatened() != null) {
            query.addCriteria(Criteria.where("isThreatened").is(speciesListQuery.getIsThreatened()));
        }
        if (speciesListQuery.getIsInvasive() != null) {
            query.addCriteria(Criteria.where("isInvasive").is(speciesListQuery.getIsInvasive()));
        }
        if (speciesListQuery.getIsBIE() != null) {
            query.addCriteria(Criteria.where("isBIE").is(speciesListQuery.getIsBIE()));
        }
        if (speciesListQuery.getIsSDS() != null) {
            query.addCriteria(Criteria.where("isSDS").is(speciesListQuery.getIsSDS()));
        }
        if (speciesListQuery.getDataResourceUid() != null) {
            if (speciesListQuery.getDataResourceUid().contains(",")) {
                List<String> dataResourceUids = Arrays.asList(speciesListQuery.getDataResourceUid().split(","));
                query.addCriteria(Criteria.where("dataResourceUid").in(dataResourceUids));
            } else {
                query.addCriteria(Criteria.where("dataResourceUid").is(speciesListQuery.getDataResourceUid()));
            }
        }
        
        // Add paging
        query.with(pageable);
        
        // Execute query
        List<SpeciesList> speciesLists = mongoTemplate.find(query, SpeciesList.class);
        
        // Get total count for pagination
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), SpeciesList.class);
        
        return new PageImpl<>(speciesLists, pageable, total);
    }
    /**
     * Builds a Criteria object that enforces document access based on user roles and additional search criteria.
     * @param userId
     * @param isAdmin
     * @param additionalCriteria
     * @return
     */
    private Criteria buildDocumentAccessCriteria(String userId, Boolean isAdmin, Criteria additionalCriteria) {
        Criteria accessCriteria;
        
        if (userId != null && !userId.isEmpty() && (isAdmin == null || !isAdmin)) {
            // Authenticated user: show all their documents (private or public) 
            // OR public documents from others
            accessCriteria = new Criteria().orOperator(
                Criteria.where("owner").is(userId),  // All documents owned by user
                Criteria.where("isPrivate").is(false)   // Public documents from anyone
            );
        } else if (isAdmin != null && isAdmin) {
            // Admin: show all documents
            accessCriteria = new Criteria();
        } else {
            // Unauthenticated: only show public documents
            accessCriteria = Criteria.where("isPrivate").is(false);
        }
        
        // Combine with any additional search criteria
        if (additionalCriteria != null) {
            return new Criteria().andOperator(accessCriteria, additionalCriteria);
        }
        
        return accessCriteria;
    }

    // Methods added from GraphQL refactoring - Dec 2025

    /**
     * Main search method for species lists with permission-aware filtering
     */
    public Page<SpeciesList> searchSpeciesLists(
            ListSearchContext context, 
            Pageable pageable) {
        
        // Build Elasticsearch query
        NativeQueryBuilder builder = NativeQuery.builder()
            .withPageable(PageRequest.of(0, 1)); // Initial query for aggregations only
        
        // Apply query with permission filters
        builder.withQuery(q -> q.bool(bq -> {
            buildListSearchQuery(context, bq);
            return bq;
        }));
        
        // Add aggregations for list counting and scoring
        addListAggregations(builder);
        
        // Execute search
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(), 
            SpeciesListIndex.class
        );
        
        // Extract list IDs and scores
        Map<String, Long> listCounts = extractListCounts(results);
        Map<String, Double> listScores = extractListScores(results);
        long totalLists = extractTotalListCount(results);
        
        // Fetch list metadata
        List<SpeciesList> lists = fetchAndEnrichLists(listCounts, listScores, context);
        
        // Apply pagination
        return paginateResults(lists, pageable, totalLists);
    }

    /**
     * Get facets for species lists with permission-aware filtering
     */
    public List<Facet> getFacetsForSpeciesLists(ListSearchContext context) {
        NativeQueryBuilder builder = NativeQuery.builder();
        
        // Apply query with permission filters
        builder.withQuery(q -> q.bool(bq -> {
            buildListSearchQuery(context, bq);
            return bq;
        }));
        
        // Add facet aggregations
        addFacetAggregations(builder);
        
        // Execute search
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(),
            SpeciesListIndex.class
        );
        
        // Process and return facets
        return processFacetResults(results);
    }

    /**
     * Builds the core Elasticsearch query with permission-aware filters
     */
    private void buildListSearchQuery(
            ListSearchContext context, 
            BoolQuery.Builder bq) {
        
        // Add search query if present
        if (StringUtils.isNotBlank(context.getSearchQuery())) {
            if ("relevance".equalsIgnoreCase(context.getSort())) {
                ElasticUtils.buildListSearchQuery(
                    context.getSearchQuery(), 
                    context.getUserId(),
                    context.isAdmin(), 
                    null, 
                    context.getFilters(), 
                    bq
                );
            } else {
                List<FieldValue> emptyList = new ArrayList<>();
                ElasticUtils.buildQuery(
                    context.getSearchQuery(),
                    emptyList,
                    context.getUserId(),
                    context.isAdmin(),
                    null,
                    context.getFilters(),
                    bq
                );
            }
        } else {
            List<FieldValue> emptyList = new ArrayList<>();
            ElasticUtils.buildQuery(
                "",
                emptyList,
                context.getUserId(),
                context.isAdmin(),
                null,
                context.getFilters(),
                bq
            );
        }
        
        // Apply permission-based filters
        applyPermissionFilters(context, bq);
    }

    /**
     * Applies permission-based filters to the query
     */
    private void applyPermissionFilters(
            ListSearchContext context, 
            BoolQuery.Builder bq) {
        
        // Check if isPrivate filter is explicitly set
        boolean hasPrivateFilter = context.getFilters().stream()
            .anyMatch(f -> "isPrivate".equals(f.getKey()));
        
        if (hasPrivateFilter) {
            // Filter is explicitly set, let it be handled by ElasticUtils
            return;
        }
        
        // Apply default permission logic
        if (!context.isAuthenticated()) {
            // Unauthenticated: only public lists
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
            
        } else if (context.isViewingOwnLists()) {
            // Viewing own lists: show all their lists (public and private)
            bq.filter(f -> f.term(t -> t.field("owner").value(context.getUserId())));
            
        } else if (!context.isAdmin()) {
            // Authenticated non-admin: only public lists (unless viewing own)
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
            
        } else if (context.isAdmin() && context.getUserId() != null) {
            // Admin viewing specific user's lists
            bq.filter(f -> f.term(t -> t.field("owner").value(context.getUserId())));
        }
        // Admin without userId specified: see all lists (no filter)
    }

    /**
     * Adds aggregations for list counting and scoring
     */
    private void addListAggregations(NativeQueryBuilder builder) {
        // Total list count
        builder.withAggregation(
            "list_count",
            Aggregation.of(a -> a.cardinality(
                ca -> ca.field(SPECIES_LIST_ID + ".keyword")
            ))
        );
        
        // List IDs with max scores
        builder.withAggregation(
            SPECIES_LIST_ID,
            Aggregation.of(a -> a
                .terms(ta -> ta
                    .field(SPECIES_LIST_ID + ".keyword")
                    .size(MAX_LIST_ENTRIES)
                )
                .aggregations("max_score",
                    Aggregation.of(ma -> ma.max(m -> m.script(s -> s.source("_score"))))
                )
            )
        );
    }

    /**
     * Adds facet aggregations with distinct list counts
     */
    private void addFacetAggregations(NativeQueryBuilder builder) {
        List<String> facetFields = Arrays.asList(
            "isAuthoritative", "listType", "isBIE", "isSDS", 
            "isPrivate", "hasRegion", "tags", "isThreatened", 
            "isInvasive", "licence"
        );
        
        Set<String> booleanFields = Set.of(
            "isAuthoritative", "isBIE", "isSDS", "hasRegion",
            "isThreatened", "isInvasive", "isPrivate"
        );
        
        for (String field : facetFields) {
            String esField = booleanFields.contains(field) 
                ? field 
                : field + ".keyword";
            
            builder.withAggregation(
                field,
                Aggregation.of(a -> a
                    .terms(ta -> ta.field(esField).size(50))
                    .aggregations("distinct_list_count",
                        Aggregation.of(ca -> ca.cardinality(
                            c -> c.field(SPECIES_LIST_ID + ".keyword")
                        ))
                    )
                )
            );
        }
    }

    /**
     * Extracts list counts from aggregation results
     */
    private Map<String, Long> extractListCounts(SearchHits<SpeciesListIndex> results) {
        ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();
        if (agg == null) return Collections.emptyMap();
        
        return agg.aggregations().stream()
            .filter(a -> SPECIES_LIST_ID.equals(a.aggregation().getName()))
            .findFirst()
            .map(a -> a.aggregation().getAggregate().sterms().buckets().array())
            .orElse(Collections.emptyList())
            .stream()
            .collect(Collectors.toMap(
                b -> b.key().stringValue(),
                MultiBucketBase::docCount
            ));
    }

    /**
     * Extracts relevance scores from aggregation results
     */
    private Map<String, Double> extractListScores(SearchHits<SpeciesListIndex> results) {
        ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();
        if (agg == null) return Collections.emptyMap();
        
        return agg.aggregations().stream()
            .filter(a -> SPECIES_LIST_ID.equals(a.aggregation().getName()))
            .findFirst()
            .map(a -> a.aggregation().getAggregate().sterms().buckets().array())
            .orElse(Collections.emptyList())
            .stream()
            .collect(Collectors.toMap(
                b -> b.key().stringValue(),
                b -> {
                    Aggregate maxScore = b.aggregations().get("max_score");
                    double score = maxScore != null ? maxScore.max().value() : 0.0;
                    return (Double.isInfinite(score) || Double.isNaN(score)) ? 0.0 : score;
                }
            ));
    }

    /**
     * Extracts total list count from aggregation results
     */
    private long extractTotalListCount(SearchHits<SpeciesListIndex> results) {
        ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();
        if (agg == null) return 0;
        
        return agg.aggregations().stream()
            .filter(a -> "list_count".equals(a.aggregation().getName()))
            .findFirst()
            .map(a -> a.aggregation().getAggregate().cardinality().value())
            .orElse(0L);
    }

    /**
     * Fetches list metadata and enriches with counts and scores
     */
    private List<SpeciesList> fetchAndEnrichLists(
            Map<String, Long> counts,
            Map<String, Double> scores,
            ListSearchContext context) {
        
        if (counts.isEmpty()) return Collections.emptyList();
        
        List<SpeciesList> lists = new ArrayList<>();
        speciesListMongoRepository.findAllById(counts.keySet())
            .forEach(list -> {
                list.setRowCount(counts.get(list.getId()).intValue());
                lists.add(list);
            });
        
        // Sort lists
        lists.sort(buildComparator(context.getSort(), context.getDir(), scores));
        
        return lists;
    }

    /**
     * Builds comparator for sorting lists
     */
    private Comparator<SpeciesList> buildComparator(
            String sort,
            String dir,
            Map<String, Double> scores) {
        
        boolean ascending = "asc".equalsIgnoreCase(dir);
        Comparator<SpeciesList> comparator;
        
        switch (sort) {
            case "relevance":
                comparator = Comparator
                    .comparing((SpeciesList list) -> scores.getOrDefault(list.getId(), 0.0))
                    .thenComparing(list -> 
                        list.getTitle() != null ? list.getTitle() : "", 
                        String.CASE_INSENSITIVE_ORDER
                    );
                return ascending ? comparator : comparator.reversed();
                
            case "title":
                comparator = Comparator.comparing(
                    SpeciesList::getTitle, 
                    String.CASE_INSENSITIVE_ORDER
                );
                break;
                
            case "listType":
                comparator = ascending
                    ? Comparator.comparing(
                        SpeciesList::getListType,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                    )
                    : Comparator.comparing(
                        SpeciesList::getListType,
                        Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)
                    );
                break;
                
            case "rowCount":
                comparator = Comparator.comparing(SpeciesList::getRowCount);
                break;
                
            case "lastUpdated":
            default:
                comparator = Comparator.comparing(SpeciesList::getLastUpdated);
                break;
        }
        
        return ascending ? comparator : comparator.reversed();
    }

    /**
     * Applies pagination to results
     */
    private Page<SpeciesList> paginateResults(
            List<SpeciesList> lists,
            Pageable pageable,
            long total) {
        
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), lists.size());
        
        List<SpeciesList> page = lists.subList(start, end);
        return new PageImpl<>(page, pageable, total);
    }

    /**
     * Processes facet aggregation results
     */
    private List<Facet> processFacetResults(SearchHits<SpeciesListIndex> results) {
        ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();
        if (agg == null) return Collections.emptyList();
        
        List<Facet> facets = new ArrayList<>();
        
        for (ElasticsearchAggregation aggResult : agg.aggregations()) {
            String fieldName = aggResult.aggregation().getName();
            Aggregate aggregate = aggResult.aggregation().getAggregate();
            
            Facet facet = new Facet();
            facet.setKey(fieldName);
            facet.setCounts(new ArrayList<>());
            
            if (aggregate.isSterms()) {
                // String terms
                aggregate.sterms().buckets().array().forEach(bucket -> {
                    long distinctCount = getDistinctListCount(bucket.aggregations());
                    facet.getCounts().add(
                        new FacetCount(bucket.key().stringValue(), distinctCount)
                    );
                });
            } else if (aggregate.isLterms()) {
                // Boolean terms
                aggregate.lterms().buckets().array().forEach(bucket -> {
                    long distinctCount = getDistinctListCount(bucket.aggregations());
                    String key = bucket.key() == 1 ? "true" : "false";
                    facet.getCounts().add(new FacetCount(key, distinctCount));
                });
            }
            
            if (!facet.getCounts().isEmpty()) {
                facets.add(facet);
            }
        }
        
        return facets;
    }

    /**
     * Extracts distinct list count from nested aggregation
     */
    private long getDistinctListCount(Map<String, Aggregate> aggregations) {
        Aggregate distinctCount = aggregations.get("distinct_list_count");
        return distinctCount != null ? distinctCount.cardinality().value() : 0;
    }

    // Helpers for list view graphql methods

    /**
     * Search items within a specific species list
     */
    public Page<SpeciesListItem> searchSingleSpeciesList(
            SingleListSearchContext context,
            Pageable pageable) {
        
        // Build Elasticsearch query
        NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageable);
        
        // Apply query with filters
        builder.withQuery(q -> q.bool(bq -> {
            buildSingleListQuery(context, bq);
            return bq;
        }));
        
        // Apply sorting
        applySorting(builder, context.getSort(), context.getDir());
        
        // Execute search
        NativeQuery query = builder.build();
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            query,
            SpeciesListIndex.class,
            IndexCoordinates.of("species-lists")
        );
        
        // Convert and return results
        List<SpeciesListItem> items = convertSearchResults(results);
        return new PageImpl<>(items, pageable, results.getTotalHits());
    }

    /**
     * Get facets for a specific species list
     */
    public List<Facet> getFacetsForSingleSpeciesList(
            SingleListSearchContext context,
            List<String> facetFields) {
        
        NativeQueryBuilder builder = NativeQuery.builder();
        
        // Build query without filters (for base aggregations)
        builder.withQuery(q -> q.bool(bq -> {
            buildBaseListQuery(context, bq);
            return bq;
        }));
        
        // Add post-filter if filters are present
        if (!context.getFilters().isEmpty()) {
            builder.withFilter(q -> q.bool(bq -> {
                buildSingleListQuery(context, bq);
                return bq;
            }));
        }
        
        // Add aggregations for facet fields
        addSingleListFacetAggregations(builder, facetFields);
        
        // Add classification aggregations
        addClassificationAggregations(builder);
        
        // Add property key aggregations
        addPropertyAggregations(builder);
        
        // Execute search
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(),
            SpeciesListIndex.class
        );
        
        // Process and return facets
        return processSingleListFacets(results, facetFields, context);
    }

    /**
     * Builds the base query for a specific list (list ID + search query only)
     */
    private void buildBaseListQuery(
            SingleListSearchContext context,
            BoolQuery.Builder bq) {
        
        // Filter by list ID
        bq.must(m -> m.term(t -> t.field(SPECIES_LIST_ID).value(context.getSpeciesListId())));
        
        // Add search query if present
        if (StringUtils.isNotBlank(context.getSearchQuery())) {
            bq.must(m -> m.queryString(
                qs -> qs.query(context.getSearchQuery())
            ));
        }
    }

    /**
     * Builds the full query including filters
     */
    private void buildSingleListQuery(
            SingleListSearchContext context,
            BoolQuery.Builder bq) {
        
        // Use existing ElasticUtils to build complete query
        ElasticUtils.buildQuery(
            context.getSearchQuery(),
            context.getSpeciesListId(),
            context.getUserId(),
            context.isAdmin(),
            null,
            context.getFilters(),
            bq
        );
    }

    /**
     * Applies sorting to the query
     */
    private void applySorting(NativeQueryBuilder builder, String sort, String dir) {
        SortOrder order = "asc".equalsIgnoreCase(dir) ? SortOrder.Asc : SortOrder.Desc;
        
        builder.withSort(s -> s.field(
            new FieldSort.Builder()
                .field(sort)
                .order(order)
                .build()
        ));
    }

    /**
     * Adds facet aggregations for the specified fields
     */
    private void addSingleListFacetAggregations(
            NativeQueryBuilder builder,
            List<String> facetFields) {
        
        if (facetFields == null || facetFields.isEmpty()) {
            return;
        }
        
        for (String field : facetFields) {
            String esField = getPropertiesFacetField(field);
            builder.withAggregation(
                field,
                Aggregation.of(a -> a.terms(ta -> ta.field(esField).size(30)))
            );
        }
    }

    /**
     * Adds classification field aggregations
     */
    private void addClassificationAggregations(NativeQueryBuilder builder) {
        List<String> classificationFields = Arrays.asList(
            "classification.family",
            "classification.order",
            "classification.class",
            "classification.phylum",
            "classification.kingdom",
            "classification.speciesSubgroup",
            "classification.rank",
            "classification.vernacularName",
            "classification.matchType"
        );
        
        for (String field : classificationFields) {
            builder.withAggregation(
                field,
                Aggregation.of(a -> a.terms(ta -> ta.field(field + ".keyword").size(500)))
            );
        }
    }

    /**
     * Adds property key aggregations for dynamic properties
     */
    private void addPropertyAggregations(NativeQueryBuilder builder) {
        builder.withAggregation(
            "properties_keys",
            Aggregation.of(a -> a
                .nested(na -> na.path("properties"))
                .aggregations("key_counts",
                    sa -> sa.terms(ta -> ta.field("properties.key.keyword").size(100))
                )
            )
        );
    }

    /**
     * Processes facet results including property facets
     */
    private List<Facet> processSingleListFacets(
            SearchHits<SpeciesListIndex> results,
            List<String> facetFields,
            SingleListSearchContext context) {
        
        ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();
        if (agg == null) {
            return Collections.emptyList();
        }
        
        List<Facet> facets = new ArrayList<>();
        
        // Process standard facets (includes classification fields)
        List<String> allFields = new ArrayList<>();
        if (facetFields != null) {
            allFields.addAll(facetFields);
        }
        
        // Add classification fields
        allFields.addAll(Arrays.asList(
            "classification.family", "classification.order", "classification.class",
            "classification.phylum", "classification.kingdom", "classification.speciesSubgroup",
            "classification.rank", "classification.vernacularName", "classification.matchType"
        ));
        
        for (String field : allFields) {
            ElasticsearchAggregation aggResult = agg.aggregations().stream()
                .filter(a -> field.equals(a.aggregation().getName()))
                .findFirst()
                .orElse(null);
            
            if (aggResult != null && aggResult.aggregation().getAggregate().isSterms()) {
                Facet facet = createFacetFromTerms(field, 
                    aggResult.aggregation().getAggregate().sterms().buckets().array());
                if (!facet.getCounts().isEmpty()) {
                    facets.add(facet);
                }
            }
        }
        
        // Process property facets
        List<Facet> propertyFacets = processPropertyFacets(agg, context);
        facets.addAll(propertyFacets);
        
        return facets;
    }

    /**
     * Creates a facet from term aggregation results
     */
    private Facet createFacetFromTerms(String fieldName, List<StringTermsBucket> buckets) {
        Facet facet = new Facet();
        facet.setKey(fieldName);
        facet.setCounts(new ArrayList<>());
        
        for (StringTermsBucket bucket : buckets) {
            facet.getCounts().add(
                new FacetCount(bucket.key().stringValue(), bucket.docCount())
            );
        }
        
        return facet;
    }

    /**
     * Processes property facets by identifying keys and their values
     */
    private List<Facet> processPropertyFacets(
            ElasticsearchAggregations agg,
            SingleListSearchContext context) {
        
        List<Facet> propertyFacets = new ArrayList<>();
        
        // Extract property keys
        List<String> propertyKeys = extractPropertyKeys(agg);
        
        // For each property key, get its values
        for (String propertyKey : propertyKeys) {
            Facet propertyFacet = getPropertyValueFacet(propertyKey, context);
            if (propertyFacet != null && !propertyFacet.getCounts().isEmpty()) {
                propertyFacets.add(propertyFacet);
            }
        }
        
        return propertyFacets;
    }

    /**
     * Extracts property keys from the properties_keys aggregation
     */
    private List<String> extractPropertyKeys(ElasticsearchAggregations agg) {
        List<String> keys = new ArrayList<>();
        
        ElasticsearchAggregation propertiesAgg = agg.aggregations().stream()
            .filter(a -> "properties_keys".equals(a.aggregation().getName()))
            .findFirst()
            .orElse(null);
        
        if (propertiesAgg == null) {
            return keys;
        }
        
        Aggregate nestedAgg = propertiesAgg.aggregation().getAggregate();
        if (nestedAgg.isNested()) {
            Aggregate keyCountsAgg = nestedAgg.nested().aggregations().get("key_counts");
            if (keyCountsAgg != null && keyCountsAgg.isSterms()) {
                keyCountsAgg.sterms().buckets().array()
                    .forEach(bucket -> keys.add(bucket.key().stringValue()));
            }
        }
        
        return keys;
    }

    /**
     * Gets facet counts for a specific property key by executing a separate query
     */
    private Facet getPropertyValueFacet(String propertyKey, SingleListSearchContext context) {
        NativeQueryBuilder builder = NativeQuery.builder();
        
        // Build base query (list ID + search query)
        builder.withQuery(q -> q.bool(bq -> {
            buildBaseListQuery(context, bq);
            return bq;
        }));
        
        // Add nested aggregation for this specific property key's values
        builder.withAggregation(
            propertyKey + "_values",
            Aggregation.of(a -> a
                .nested(na -> na.path("properties"))
                .aggregations("filtered_values", 
                    sa -> sa
                        .filter(f -> f.term(t -> t
                            .field("properties.key.keyword")
                            .value(propertyKey)
                        ))
                        .aggregations("value_counts",
                            va -> va.terms(ta -> ta
                                .field("properties.value.keyword")
                                .size(100)
                            )
                        )
                )
            )
        );
        
        // Execute query
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(),
            SpeciesListIndex.class
        );
        
        // Extract results
        return extractPropertyValueFacet(propertyKey, results);
    }

    /**
     * Extracts property value facet from query results
     */
    private Facet extractPropertyValueFacet(String propertyKey, SearchHits<SpeciesListIndex> results) {
        ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();
        if (agg == null) {
            return null;
        }
        
        ElasticsearchAggregation keyValueAgg = agg.aggregations().stream()
            .filter(a -> (propertyKey + "_values").equals(a.aggregation().getName()))
            .findFirst()
            .orElse(null);
        
        if (keyValueAgg == null) {
            return null;
        }
        
        Aggregate nestedAgg = keyValueAgg.aggregation().getAggregate();
        if (!nestedAgg.isNested()) {
            return null;
        }
        
        Aggregate filteredValuesAgg = nestedAgg.nested().aggregations().get("filtered_values");
        if (filteredValuesAgg == null || !filteredValuesAgg.isFilter()) {
            return null;
        }
        
        Aggregate valueCountsAgg = filteredValuesAgg.filter().aggregations().get("value_counts");
        if (valueCountsAgg == null || !valueCountsAgg.isSterms()) {
            return null;
        }
        
        List<StringTermsBucket> valueBuckets = valueCountsAgg.sterms().buckets().array();
        if (valueBuckets.isEmpty()) {
            return null;
        }
        
        Facet facet = new Facet();
        facet.setKey("properties." + propertyKey);
        facet.setCounts(new ArrayList<>());
        
        for (StringTermsBucket bucket : valueBuckets) {
            facet.getCounts().add(
                new FacetCount(bucket.key().stringValue(), bucket.docCount())
            );
        }
        
        return facet;
    }

    /**
     * Converts search results to SpeciesListItem list
     */
    private List<SpeciesListItem> convertSearchResults(SearchHits<SpeciesListIndex> results) {
        Object unwrapped = SearchHitSupport.unwrapSearchHits(results);
        
        if (!(unwrapped instanceof List)) {
            throw new IllegalStateException("unwrapSearchHits did not return a List");
        }
        
        @SuppressWarnings("unchecked")
        List<SpeciesListIndex> indexes = (List<SpeciesListIndex>) unwrapped;
        
        return ElasticUtils.convertList(indexes);
    }

    /**
     * Helper method to determine the correct Elasticsearch field name
     */
    private String getPropertiesFacetField(String filter) {
        if (CORE_FIELDS.contains(filter) || 
            filter.startsWith("classification.") || 
            filter.startsWith("licence")) {
            return filter + ".keyword";
        }
        return "properties." + filter + ".keyword";
    }
}
