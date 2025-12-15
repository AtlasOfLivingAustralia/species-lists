package au.org.ala.listsapi.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.FacetCount;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.util.ElasticsearchQueryBuilder;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketBase;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;

/**
 * Service for searching and faceting species lists (the "lists" view).
 * Handles list-level operations including aggregations by list ID,
 * metadata fetching, and pagination.
 */
@Service
public class SpeciesListSearchService {

    private static final org.slf4j.Logger logger =
        org.slf4j.LoggerFactory.getLogger(SpeciesListSearchService.class);

    private static final String SPECIES_LIST_ID = "speciesListID";
    private static final int MAX_LIST_ENTRIES = 10000;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SpeciesListMongoRepository speciesListMongoRepository;

    /**
     * Main search method for species lists with permission-aware filtering
     */
    public Page<SpeciesList> searchSpeciesLists(
            ListSearchContext context,
            Pageable pageable) {

        NativeQueryBuilder builder = new NativeQueryBuilder();
            builder.withPageable(PageRequest.of(0, 1));

        builder.withQuery(q -> q.bool(bq -> {
            buildListSearchQuery(context, bq);
            return bq;
        }));

        addListAggregations(builder);

        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(),
            SpeciesListIndex.class
        );

        Map<String, Long> listCounts = extractListCounts(results);
        Map<String, Double> listScores = extractListScores(results);
        long totalLists = extractTotalListCount(results);

        List<SpeciesList> lists = fetchAndEnrichLists(listCounts, listScores, context);

        return paginateResults(lists, pageable, totalLists);
    }

    /**
     * Get facets for species lists with permission-aware filtering
     */
    public List<Facet> getFacetsForSpeciesLists(ListSearchContext context) {
        NativeQueryBuilder builder = new NativeQueryBuilder();

        builder.withQuery(q -> q.bool(bq -> {
            buildListSearchQuery(context, bq);
            return bq;
        }));

        addFacetAggregations(builder);

        SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
            builder.build(),
            SpeciesListIndex.class
        );

        return processFacetResults(results);
    }

    /**
     * Builds the core Elasticsearch query with permission-aware filters
     */
    private void buildListSearchQuery(
            ListSearchContext context,
            BoolQuery.Builder bq) {

        if (StringUtils.isNotBlank(context.getSearchQuery())) {
            if ("relevance".equalsIgnoreCase(context.getSort())) {
                ElasticsearchQueryBuilder.buildListSearchQuery(
                    context.getSearchQuery(),
                    context.getUserId(),
                    context.isAdmin(),
                    null,
                    context.getFilters(),
                    bq
                );
            } else {
                List<FieldValue> emptyList = new ArrayList<>();
                ElasticsearchQueryBuilder.buildQuery(
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
            ElasticsearchQueryBuilder.buildQuery(
                "",
                emptyList,
                context.getUserId(),
                context.isAdmin(),
                null,
                context.getFilters(),
                bq
            );
        }

        applyPermissionFilters(context, bq);
    }

    /**
     * Applies permission-based filters to the query
     */
    private void applyPermissionFilters(
            ListSearchContext context,
            BoolQuery.Builder bq) {

        boolean hasPrivateFilter = context.getFilters().stream()
            .anyMatch(f -> "isPrivate".equals(f.getKey()));

        if (hasPrivateFilter) {
            return;
        }

        if (!context.isAuthenticated()) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
        } else if (context.isViewingOwnLists()) {
            bq.filter(f -> f.term(t -> t.field("owner").value(context.getUserId())));
        } else if (!context.isAdmin()) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
        } else if (context.isAdmin() && context.getUserId() != null) {
            bq.filter(f -> f.term(t -> t.field("owner").value(context.getUserId())));
        }

        logger.debug("Applied permission filters for user: {}, isAdmin: {}, viewingOwnLists: {}",
            context.getUserId(),
            context.isAdmin(),
            context.isViewingOwnLists()
        );
    }

    /**
     * Adds aggregations for list counting and scoring
     */
    private void addListAggregations(NativeQueryBuilder builder) {
        builder.withAggregation(
            "list_count",
            Aggregation.of(a -> a.cardinality(
                ca -> ca.field(SPECIES_LIST_ID + ".keyword")
            ))
        );

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
        List<String> facetFields = List.of(
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
                aggregate.sterms().buckets().array().forEach(bucket -> {
                    long distinctCount = getDistinctListCount(bucket.aggregations());
                    facet.getCounts().add(
                        new FacetCount(bucket.key().stringValue(), distinctCount)
                    );
                });
            } else if (aggregate.isLterms()) {
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
}