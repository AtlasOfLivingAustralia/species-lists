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
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.util.ElasticUtils;
import au.org.ala.ws.security.profile.AlaUserProfile;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;

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
            Pageable pageableRequest = PageRequest.of(page - 1, pageSize);
            NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
            builder.withQuery(
                    q -> q.bool(
                            bq -> {
                                ElasticUtils.buildQuery(ElasticUtils.cleanRawQuery(searchQuery), validIDs, null, isAdmin, null, tempFilters, bq);
                                ElasticUtils.restrictFields(searchQuery, restrictedFields, bq);
                                return bq;
                            }));

            builder.withSort(
                    s -> s.field(
                            new FieldSort.Builder()
                                    .field(emptyDefault(sort, "scientificName"))
                                    .order(emptyDefault(dir, "asc").equals("asc") ? SortOrder.Asc : SortOrder.Desc)
                                    .build()));

            NativeQuery query = builder.build();
            query.setPageable(pageableRequest);
            SearchHits<SpeciesListIndex> results =
                    elasticsearchOperations.search(
                            query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

            List<SpeciesListItem> speciesListItems =
                    ElasticUtils.convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));

            return speciesListItems;
        }

        return new ArrayList<>();
    }

    private String emptyDefault(String value, String defaultValue) {
        return StringUtils.isNotEmpty(value) ? value : defaultValue;
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
            query.addCriteria(Criteria.where("dataResourceUid").is(speciesListQuery.getDataResourceUid()));
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
}
