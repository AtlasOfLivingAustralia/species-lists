package au.org.ala.listsapi.service;

import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
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
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.util.ElasticsearchQueryBuilder;
import au.org.ala.ws.security.profile.AlaUserProfile;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;

/**
 * Service for searching and faceting items within species lists.
 * Handles item-level operations including CRUD operations on list items,
 * bulk operations, and single-list searches.
 */
@Service
public class SpeciesListItemService {

    private static final org.slf4j.Logger logger =
        org.slf4j.LoggerFactory.getLogger(SpeciesListItemService.class);

    private static final String SPECIES_LIST_ID = "speciesListID";

    private static final Set<String> CORE_FIELDS = Set.of(
        "id", "scientificName", "vernacularName", "licence", "taxonID",
        "kingdom", "phylum", "class", "order", "family", "genus",
        "isBIE", "listType", "isAuthoritative", "hasRegion", "isSDS",
        "isThreatened", "isInvasive", "isPrivate", "tags"
    );

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SpeciesListMongoRepository speciesListMongoRepository;

    // ========================================================================
    // BULK OPERATIONS
    // ========================================================================

    /**
     * Performs a bulk update on a list of SpeciesListItem objects
     */
    public BulkWriteResult speciesListItemsBulkUpdate(
            List<SpeciesListItem> items,
            List<String> keys) {

        BulkOperations bulkOps = mongoTemplate.bulkOps(
            BulkOperations.BulkMode.UNORDERED,
            SpeciesListItem.class
        );

        for (SpeciesListItem item : items) {
            Query query = new Query(Criteria.where("_id").is(item.getId()));
            Update update = new Update();
            keys.forEach(key -> update.set(key, item.getPropFromKey(key)));
            bulkOps.upsert(query, update);
        }

        return bulkOps.execute();
    }

    /**
     * Performs a bulk save on a list of SpeciesListItem objects
     */
    public BulkWriteResult speciesListItemsBulkSave(List<SpeciesListItem> items) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(
            BulkOperations.BulkMode.UNORDERED,
            SpeciesListItem.class
        );
        bulkOps.insert(items);
        return bulkOps.execute();
    }

    // ========================================================================
    // SINGLE LIST SEARCH & FACETING
    // ========================================================================

    /**
     * Search items within a specific species list
     */
    public Page<SpeciesListItem> searchSingleSpeciesList(
            SingleListSearchContext context,
            Pageable pageable) {

        NativeQueryBuilder builder = new NativeQueryBuilder().withPageable(pageable);

        builder.withQuery(q -> q.bool(bq -> {
            buildSingleListQuery(context, bq);
            return bq;
        }));

        applySorting(builder, context.getSort(), context.getDir());

        NativeQuery query = builder.build();
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            query,
            SpeciesListIndex.class,
            IndexCoordinates.of("species-lists")
        );

        List<SpeciesListItem> items = convertSearchResults(results);
        return new PageImpl<>(items, pageable, results.getTotalHits());
    }

    /**
     * Get facets for a specific species list
     */
    public List<Facet> getFacetsForSingleSpeciesList(
            SingleListSearchContext context,
            List<String> facetFields) {

        NativeQueryBuilder builder = new NativeQueryBuilder();

        builder.withQuery(q -> q.bool(bq -> {
            buildBaseListQuery(context, bq);
            return bq;
        }));

        if (!context.getFilters().isEmpty()) {
            builder.withFilter(q -> q.bool(bq -> {
                buildSingleListQuery(context, bq);
                return bq;
            }));
        }

        addSingleListFacetAggregations(builder, facetFields);
        addClassificationAggregations(builder);
        addPropertyAggregations(builder);

        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(),
            SpeciesListIndex.class
        );

        return processSingleListFacets(results, facetFields, context);
    }

    /**
     * Builds the base query for a specific list (list ID + search query only)
     */
    private void buildBaseListQuery(
            SingleListSearchContext context,
            BoolQuery.Builder bq) {

        bq.must(m -> m.term(t -> t.field(SPECIES_LIST_ID).value(context.getSpeciesListId())));

        if (StringUtils.isNotBlank(context.getSearchQuery())) {
            bq.must(m -> m.queryString(qs -> qs.query(context.getSearchQuery())));
        }
    }

    /**
     * Builds the full query including filters
     */
    private void buildSingleListQuery(
            SingleListSearchContext context,
            BoolQuery.Builder bq) {

        ElasticsearchQueryBuilder.buildQuery(
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
            "classification.family", "classification.order", "classification.class",
            "classification.phylum", "classification.kingdom", "classification.speciesSubgroup",
            "classification.rank", "classification.vernacularName", "classification.matchType"
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

        List<String> allFields = new ArrayList<>();
        if (facetFields != null) {
            allFields.addAll(facetFields);
        }

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
                Facet facet = createFacetFromTerms(
                    field,
                    aggResult.aggregation().getAggregate().sterms().buckets().array()
                );
                if (!facet.getCounts().isEmpty()) {
                    facets.add(facet);
                }
            }
        }

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
        List<String> propertyKeys = extractPropertyKeys(agg);

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
        NativeQueryBuilder builder = new NativeQueryBuilder();

        builder.withQuery(q -> q.bool(bq -> {
            buildBaseListQuery(context, bq);
            return bq;
        }));

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

        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(),
            SpeciesListIndex.class
        );

        return extractPropertyValueFacet(propertyKey, results);
    }

    /**
     * Extracts property value facet from query results
     */
    private Facet extractPropertyValueFacet(
            String propertyKey,
            SearchHits<SpeciesListIndex> results) {

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

    // ========================================================================
    // FETCH OPERATIONS
    // ========================================================================

    /**
     * Fetches species list items based on GUIDs and optional species list IDs
     */
    public List<SpeciesListItem> fetchSpeciesListItems(
            String guids,
            @Nullable String speciesListIDs,
            int page,
            int pageSize,
            Principal principal) {

        AlaUserProfile profile = authUtils.getUserProfile(principal);
        List<FieldValue> GUIDs = Arrays.stream(guids.split(","))
            .map(FieldValue::of)
            .toList();
        List<FieldValue> listIDs = speciesListIDs != null
            ? Arrays.stream(speciesListIDs.split(",")).map(FieldValue::of).toList()
            : null;

        if (page < 1 || (page * pageSize) > 10000) {
            return new ArrayList<>();
        }

        Pageable pageableRequest = PageRequest.of(page - 1, pageSize);
        NativeQueryBuilder builder = new NativeQueryBuilder().withPageable(pageableRequest);

        builder.withQuery(q -> q.bool(bq -> {
            bq.filter(f -> f.terms(t -> t
                .field("classification.taxonConceptID.keyword")
                .terms(ta -> ta.value(GUIDs))));

            if (listIDs != null) {
                bq.filter(f -> f.bool(b -> b
                    .should(s -> s.terms(t -> t
                        .field("speciesListID.keyword")
                        .terms(ta -> ta.value(listIDs))))
                    .should(s -> s.terms(t -> t
                        .field("dataResourceUid.keyword")
                        .terms(ta -> ta.value(listIDs))))
                ));
            }

            if (!authUtils.isAuthenticated(principal)) {
                logger.debug("Filtering for public lists only (user not authenticated)");
                bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
            } else if (!authUtils.hasAdminRole(profile) && !authUtils.hasInternalScope(profile)) {
                logger.debug("Filtering for private lists only (non-admin/non-internal users)");
                bq.filter(f -> f.bool(b -> b
                    .should(s -> s.bool(b2 -> b2
                        .must(m -> m.term(t -> t.field("isPrivate").value(true)))
                    ))
                    .should(s -> s.term(t -> t.field("isPrivate").value(false)))
                ));
            }

            return bq;
        }));

        NativeQuery query = builder.build();
        query.setPageable(pageableRequest);
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            query,
            SpeciesListIndex.class,
            IndexCoordinates.of("species-lists")
        );

        return convertSearchResults(results);
    }

    /**
     * Fetches species list items based on species list IDs with optional search and field restrictions
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
        List<SpeciesList> foundLists = speciesListMongoRepository
            .findAllByDataResourceUidIsInOrIdIsIn(IDs, IDs);

        HashSet<String> restrictedFields = new HashSet<>();
        if (fields != null && !fields.isBlank()) {
            restrictedFields.addAll(Arrays.stream(fields.split(","))
                .collect(Collectors.toSet()));
        }

        if (foundLists.isEmpty()) {
            return new ArrayList<>();
        }

        List<FieldValue> validIDs = foundLists.stream()
            .filter(list -> !list.getIsPrivate() || authUtils.isAuthorized(list, principal))
            .map(list -> FieldValue.of(list.getId()))
            .toList();

        if ((page - 1) * pageSize + pageSize > 10000 && pageSize > 0) {
            return new ArrayList<>();
        } else if ((page - 1) * pageSize + pageSize > 10000) {
            throw new IllegalArgumentException(
                "Page size exceeds ElasticSearch limit of 10,000 documents."
            );
        }

        if (page < 1 || validIDs.isEmpty()) {
            return new ArrayList<>();
        }

        Boolean isAdmin = principal != null
            ? (authUtils.hasAdminRole(authUtils.getUserProfile(principal)) ||
               authUtils.hasInternalScope(authUtils.getUserProfile(principal)))
            : false;

        ArrayList<Filter> tempFilters = new ArrayList<>();

        String sortField = (sort != null && !sort.isBlank()) ? sort : "scientificName";
        String sortDir = (dir != null && !dir.isBlank()) ? dir : "asc";

        Sort springSort = Sort.by(
            "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC,
            sortField
        );

        Pageable pageableRequest = PageRequest.of(page - 1, pageSize, springSort);
        NativeQueryBuilder builder = new NativeQueryBuilder().withPageable(pageableRequest);

        builder.withQuery(q -> q.bool(bq -> {
            ElasticsearchQueryBuilder.buildQuery(
                ElasticsearchQueryBuilder.cleanRawQuery(searchQuery),
                validIDs,
                null,
                isAdmin,
                null,
                tempFilters,
                bq
            );
            ElasticsearchQueryBuilder.restrictFields(searchQuery, restrictedFields, bq);
            return bq;
        }));

        NativeQuery query = builder.build();
        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            query,
            SpeciesListIndex.class,
            IndexCoordinates.of("species-lists")
        );

        if (!results.isEmpty()) {
            SpeciesListIndex firstItem = results.getSearchHit(0).getContent();
            logger.debug("First item: " + firstItem);
        }

        return convertSearchResults(results);
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Finds common keys across multiple SpeciesList objects
     */
    public static Set<String> findCommonKeys(List<SpeciesList> lists) {
        if (lists == null || lists.isEmpty()) {
            return Collections.emptySet();
        }

        if (lists.size() == 1) {
            return new HashSet<>(lists.get(0).getFieldList());
        }

        lists.sort(Comparator.comparingInt(l -> l.getFieldList().size()));

        Set<String> common = new HashSet<>(lists.get(0).getFieldList());

        for (int i = 1; i < lists.size(); i++) {
            common.retainAll(lists.get(i).getFieldList());
            if (common.isEmpty()) {
                break;
            }
        }

        return common;
    }

    /**
     * Searches SpeciesList documents based on various criteria
     */
    public Page<SpeciesList> searchDocuments(
            SpeciesList speciesListQuery,
            String userId,
            Boolean isAdmin,
            String searchTerm,
            Pageable pageable) {

        Criteria searchCriteria = new Criteria().orOperator(
            Criteria.where("title").regex(searchTerm, "i"),
            Criteria.where("description").regex(searchTerm, "i")
        );

        Criteria finalCriteria = buildDocumentAccessCriteria(userId, isAdmin, searchCriteria);
        Query query = new Query(finalCriteria);

        if (speciesListQuery.getIsAuthoritative() != null) {
            query.addCriteria(Criteria.where("isAuthoritative")
                .is(speciesListQuery.getIsAuthoritative()));
        }
        if (speciesListQuery.getIsThreatened() != null) {
            query.addCriteria(Criteria.where("isThreatened")
                .is(speciesListQuery.getIsThreatened()));
        }
        if (speciesListQuery.getIsInvasive() != null) {
            query.addCriteria(Criteria.where("isInvasive")
                .is(speciesListQuery.getIsInvasive()));
        }
        if (speciesListQuery.getIsBIE() != null) {
            query.addCriteria(Criteria.where("isBIE").is(speciesListQuery.getIsBIE()));
        }
        if (speciesListQuery.getIsSDS() != null) {
            query.addCriteria(Criteria.where("isSDS").is(speciesListQuery.getIsSDS()));
        }
        if (speciesListQuery.getDataResourceUid() != null) {
            if (speciesListQuery.getDataResourceUid().contains(",")) {
                List<String> dataResourceUids = Arrays.asList(
                    speciesListQuery.getDataResourceUid().split(",")
                );
                query.addCriteria(Criteria.where("dataResourceUid").in(dataResourceUids));
            } else {
                query.addCriteria(Criteria.where("dataResourceUid")
                    .is(speciesListQuery.getDataResourceUid()));
            }
        }

        query.with(pageable);

        List<SpeciesList> speciesLists = mongoTemplate.find(query, SpeciesList.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), SpeciesList.class);

        return new PageImpl<>(speciesLists, pageable, total);
    }

    /**
     * Builds access criteria for document searches
     */
    private Criteria buildDocumentAccessCriteria(
            String userId,
            Boolean isAdmin,
            Criteria additionalCriteria) {

        Criteria accessCriteria;

        if (userId != null && !userId.isEmpty() && (isAdmin == null || !isAdmin)) {
            accessCriteria = new Criteria().orOperator(
                Criteria.where("owner").is(userId),
                Criteria.where("isPrivate").is(false)
            );
        } else if (isAdmin != null && isAdmin) {
            accessCriteria = new Criteria();
        } else {
            accessCriteria = Criteria.where("isPrivate").is(false);
        }

        if (additionalCriteria != null) {
            return new Criteria().andOperator(accessCriteria, additionalCriteria);
        }

        return accessCriteria;
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

        return indexes.stream()
            .map(this::convertIndexToItem)
            .collect(Collectors.toList());
    }

    /**
     * Converts a SpeciesListIndex to SpeciesListItem
     */
    private SpeciesListItem convertIndexToItem(SpeciesListIndex index) {
        SpeciesListItem item = new SpeciesListItem();
        item.setId(new ObjectId(index.getId()));
        item.setSpeciesListID(index.getSpeciesListID());
        item.setSuppliedName(index.getSuppliedName());
        item.setScientificName(index.getScientificName());
        item.setVernacularName(index.getVernacularName());
        item.setPhylum(index.getPhylum());
        item.setClasss(index.getClasss());
        item.setOrder(index.getOrder());
        item.setFamily(index.getFamily());
        item.setGenus(index.getGenus());
        item.setTaxonID(index.getTaxonID());
        item.setKingdom(index.getKingdom());
        item.setProperties(index.getProperties());
        item.setClassification(index.getClassification());
        item.setDateCreated(parsedDate(index.getDateCreated()));
        item.setLastUpdated(parsedDate(index.getLastUpdated()));
        item.setLastUpdatedBy(index.getLastUpdatedBy());
        return item;
    }

    /**
     * Parses a date string
     */
    private static Date parsedDate(String date) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(date);
        } catch (Exception e) {
            return null;
        }
    }
}
