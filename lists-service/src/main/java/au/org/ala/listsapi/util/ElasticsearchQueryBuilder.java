package au.org.ala.listsapi.util;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import au.org.ala.listsapi.model.Filter;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;

/**
 * Pure utility class for building Elasticsearch queries.
 * Contains only static methods with no Spring dependencies.
 * All methods are stateless and thread-safe.
 */
public final class ElasticsearchQueryBuilder {

    private ElasticsearchQueryBuilder() {
        // Prevent instantiation
    }

    public static final List<String> CORE_FIELDS = List.of(
        "id", "scientificName", "vernacularName", "licence", "taxonID",
        "kingdom", "phylum", "class", "order", "family", "genus",
        "isBIE", "listType", "isAuthoritative", "hasRegion", "isSDS",
        "isThreatened", "isInvasive", "isPrivate", "tags"
    );

    public static final List<String> CORE_BOOL_FIELDS = List.of(
        "isBIE", "isAuthoritative", "hasRegion", "isSDS",
        "isThreatened", "isInvasive", "isPrivate"
    );

    private static final Set<String> TOP_LEVEL_SEARCHABLE_FIELDS = Set.of(
        "classs", "dataResourceUid", "family", "genus", "kingdom",
        "listType", "order", "owner", "phylum", "scientificName",
        "speciesListName", "tags",
        "classification.classs", "classification.family", "classification.genus",
        "classification.kingdom", "classification.order", "classification.phylum",
        "classification.rank", "classification.scientificName",
        "classification.scientificNameAuthorship", "classification.species",
        "classification.speciesGroup", "classification.taxonConceptID",
        "classification.vernacularName"
    );

    /**
     * Cleans and escapes a search query string
     */
    @NotNull
    public static String cleanRawQuery(String searchQuery) {
        if (searchQuery != null) {
            return searchQuery.trim().replace("\"", "\\\"");
        }
        return "";
    }

    /**
     * Builds a standard query for searching species list items
     */
    public static BoolQuery.Builder buildQuery(
            String searchQuery,
            String speciesListID,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            List<Filter> filters,
            BoolQuery.Builder bq) {

        addCommonQueryLogic(searchQuery, userId, isAdmin, isPrivate, bq);

        if (speciesListID != null) {
            bq.filter(f -> f.term(t -> t.field("speciesListID").value(speciesListID)));
        }

        addFilters(filters, bq);
        return bq;
    }

    /**
     * Builds a query for multiple species list IDs
     */
    public static void buildQuery(
            String searchQuery,
            List<FieldValue> speciesListIDs,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            List<Filter> filters,
            BoolQuery.Builder bq) {

        addCommonQueryLogic(searchQuery, userId, isAdmin, isPrivate, bq);

        if (speciesListIDs != null && !speciesListIDs.isEmpty()) {
            bq.filter(f -> f.terms(t -> t.field("speciesListID")
                .terms(ta -> ta.value(speciesListIDs))));
        }

        addFilters(filters, bq);
    }

    /**
     * Builds a query specifically for searching species lists with prioritized relevance scoring
     */
    public static BoolQuery.Builder buildListSearchQuery(
            String searchQuery,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            List<Filter> filters,
            BoolQuery.Builder bq) {

        addListSearchQueryLogic(searchQuery, userId, isAdmin, isPrivate, bq);
        addFilters(filters, bq);
        return bq;
    }

    /**
     * Adds common search query logic for item searches
     */
    private static void addCommonQueryLogic(
            String searchQuery,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            BoolQuery.Builder bq) {

        if (StringUtils.isNotBlank(searchQuery)) {
            bq.should(m -> m.matchPhrasePrefix(mpq -> mpq
                .field("all")
                .query(searchQuery.toLowerCase())
                .boost(2.0f)));
        } else {
            bq.must(m -> m.matchAll(ma -> ma));
        }

        if (StringUtils.trimToNull(searchQuery) != null && searchQuery.length() > 1) {
            bq.minimumShouldMatch("1");
        }

        // Add userId filter for my-lists view
        if (userId != null || (!isAdmin && isPrivate != null && isPrivate)) {
            bq.filter(f -> f.term(t -> t.field("owner").value(userId)));
        }

        // Add isPrivate filter
        if (userId == null && !isAdmin) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
        } else if (isPrivate != null) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(isPrivate)));
        }
    }

    /**
     * Adds search query logic for list searches with prioritized relevance scoring
     */
    private static void addListSearchQueryLogic(
            String searchQuery,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            BoolQuery.Builder bq) {

        if (StringUtils.trimToNull(searchQuery) != null && searchQuery.length() > 1) {
            // Primary: exact match on speciesListName
            bq.should(s -> s.matchPhrase(mp -> mp
                .field("speciesListName.search")
                .query(searchQuery.toLowerCase())
                .boost(100.0f)));

            // Secondary: prefix match on speciesListName
            bq.should(s -> s.prefix(p -> p
                .field("speciesListName.keyword")
                .value(searchQuery)
                .boost(75.0f)));

            // Tertiary: fuzzy match on speciesListName
            bq.should(s -> s.fuzzy(f -> f
                .field("speciesListName.search")
                .value(searchQuery.toLowerCase())
                .fuzziness("AUTO")
                .boost(50.0f)));

            // Lower priority: search across all fields
            bq.should(s -> s.matchPhrase(mp -> mp
                .field("all")
                .query(searchQuery + "*")));

            bq.minimumShouldMatch("1");
        }

        // Add userId filter
        if (userId != null || (!isAdmin && isPrivate != null && isPrivate)) {
            bq.filter(f -> f.term(t -> t.field("owner").value(userId)));
        }

        if (userId == null && !isAdmin) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
        } else if (isPrivate != null) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(isPrivate)));
        }
    }

    /**
     * Adds filters to the query
     */
    private static void addFilters(List<Filter> filters, BoolQuery.Builder bq) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        Map<String, List<Filter>> filtersByKey = filters.stream()
            .collect(Collectors.groupingBy(Filter::getKey));

        filtersByKey.forEach((key, filtersForKey) -> {
            if (key.startsWith("properties.")) {
                addPropertyFilters(key, filtersForKey, bq);
            } else {
                addStandardFilters(filtersForKey, bq);
            }
        });
    }

    /**
     * Adds property filters (nested query)
     */
    private static void addPropertyFilters(
            String key,
            List<Filter> filtersForKey,
            BoolQuery.Builder bq) {

        String propertyField = key.substring("properties.".length());
        List<String> values = filtersForKey.stream()
            .map(Filter::getValue)
            .toList();

        bq.must(m -> m.nested(n -> n
            .path("properties")
            .query(q -> q.bool(propBq -> {
                propBq.must(pm -> pm.term(pt -> pt
                    .field("properties.key.keyword")
                    .value(propertyField)));

                if (values.size() == 1) {
                    propBq.must(pm -> pm.term(pt -> pt
                        .field("properties.value.keyword")
                        .value(values.get(0))));
                } else {
                    propBq.must(pm -> pm.bool(valuesBq -> {
                        values.forEach(value ->
                            valuesBq.should(s -> s.term(t -> t
                                .field("properties.value.keyword")
                                .value(value))));
                        return valuesBq;
                    }));
                }
                return propBq;
            }))
        ));
    }

    /**
     * Adds standard (non-property) filters
     */
    private static void addStandardFilters(
            List<Filter> filtersForKey,
            BoolQuery.Builder bq) {

        bq.must(keyQuery -> keyQuery.bool(keyBool -> {
            if (filtersForKey.size() == 1) {
                Filter filter = filtersForKey.get(0);
                keyBool.must(m -> m.term(t -> t
                    .field(getPropertiesFacetField(filter.getKey()))
                    .value(filter.getValue())));
            } else {
                filtersForKey.forEach(filter ->
                    keyBool.should(m -> m.term(t -> t
                        .field(getPropertiesFacetField(filter.getKey()))
                        .value(filter.getValue()))));
                keyBool.minimumShouldMatch("1");
            }
            return keyBool;
        }));
    }

    /**
     * Restricts search to specific fields
     */
    public static void restrictFields(
            String searchQuery,
            HashSet<String> restrictedFields,
            BoolQuery.Builder mainBq) {

        String search = cleanRawQuery(searchQuery);

        if (restrictedFields == null || restrictedFields.isEmpty() || search.trim().isEmpty()) {
            return;
        }

        BoolQuery.Builder outerDisjunctionBq = new BoolQuery.Builder();

        for (String field : restrictedFields) {
            if (TOP_LEVEL_SEARCHABLE_FIELDS.contains(field)) {
                String actualFieldToSearch = field + ".search";
                outerDisjunctionBq.should(s -> s.matchPhrase(mp -> mp
                    .field(actualFieldToSearch)
                    .query(search)));
            } else {
                // Nested properties
                outerDisjunctionBq.should(s -> s.nested(n -> n
                    .path("properties")
                    .scoreMode(ChildScoreMode.Avg)
                    .query(nq -> nq.bool(nb -> {
                        nb.must(m1 -> m1.term(t -> t
                            .field("properties.key")
                            .value(field)));
                        nb.must(m2 -> m2.matchPhrase(mp -> mp
                            .field("properties.value")
                            .query(search)));
                        return nb;
                    }))));
            }
        }

        outerDisjunctionBq.minimumShouldMatch("1");
        mainBq.must(m -> m.bool(outerDisjunctionBq.build()));
    }

    /**
     * Gets the correct Elasticsearch field name for faceting
     */
    public static String getPropertiesFacetField(String filter) {
        if (CORE_BOOL_FIELDS.contains(filter)) {
            return filter;
        }
        if (CORE_FIELDS.contains(filter) || filter.startsWith("classification.")) {
            return filter + ".keyword";
        }
        return "properties." + filter + ".keyword";
    }
}