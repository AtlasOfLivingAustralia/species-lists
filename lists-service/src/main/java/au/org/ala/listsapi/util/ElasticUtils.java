/**
 * Copyright (c) 2025 Atlas of Living Australia
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

package au.org.ala.listsapi.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import au.org.ala.listsapi.controller.AuthUtils;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.ReleaseMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.MetadataService;
import au.org.ala.listsapi.service.TaxonService;
import au.org.ala.listsapi.service.ValidationService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.json.JsonData;
import jakarta.annotation.PostConstruct;

/** GraphQL API for lists */
@Component
public class ElasticUtils {
    public static final List<String> CORE_FIELDS =
            List.of(
                    "id",
                    "scientificName",
                    "vernacularName",
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
                    "tags");

    public static final List<String> CORE_BOOL_FIELDS =
            List.of("isBIE", "isAuthoritative", "hasRegion", "isSDS", 
                    "isThreatened", "isInvasive");

    private static final Set<String> TOP_LEVEL_SEARCHABLE_FIELDS = Set.of(
            // Root-level fields that have a ".search" subfield
            "classs",            // top-level "classs.search"
            "dataResourceUid",   // dataResourceUid.search
            "family",            // family.search
            "genus",             // genus.search
            "kingdom",           // kingdom.search
            "listType",          // listType.search
            "order",             // order.search
            "owner",             // owner.search
            "phylum",            // phylum.search
            "scientificName",    // scientificName.search
            "speciesListName",   // speciesListName.search
            "tags",              // tags.search

            // classification.* subfields that have a ".search" subfield
            "classification.classs",                 // classification.classs.search
            "classification.family",                 // classification.family.search
            "classification.genus",                  // classification.genus.search
            "classification.kingdom",                // classification.kingdom.search
            "classification.order",                  // classification.order.search
            "classification.phylum",                 // classification.phylum.search
            "classification.rank",                   // classification.rank.search
            "classification.scientificName",         // classification.scientificName.search
            "classification.scientificNameAuthorship", // classification.scientificNameAuthorship.search
            "classification.species",                // classification.species.search
            "classification.speciesGroup",           // classification.speciesGroup.search
            "classification.taxonConceptID",         // classification.taxonConceptID.search
            "classification.vernacularName"          // classification.vernacularName.search
    );

    public static final String SPECIES_LIST_ID = "speciesListID";
    private static final Logger logger = LoggerFactory.getLogger(ElasticUtils.class);
    @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
    @Autowired protected ReleaseMongoRepository releaseMongoRepository;
    @Autowired protected ElasticsearchOperations elasticsearchOperations;
    @Autowired protected ElasticsearchClient elasticsearchClient;

    @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
    @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @Autowired protected TaxonService taxonService;
    @Autowired protected ValidationService validationService;
    @Autowired protected AuthUtils authUtils;
    @Autowired protected MetadataService metadataService;

    @Value("${elastic.maximumDocuments}")
    public static final int MAX_LIST_ENTRIES = 10000;

    @Value("${elastic.indexName}")
    public static final String INDEX_NAME = "species-lists";

    @PostConstruct
    public void updateIndexSettings() {
        try {
            Map<String, JsonData> settingsMap = new HashMap<>();
            settingsMap.put("index.max_result_window", JsonData.of(50000));
            
            PutIndicesSettingsRequest updateSettingsRequest = PutIndicesSettingsRequest.of(b -> b
                .index(INDEX_NAME)
                .settings(IndexSettings.of(s -> s
                    .otherSettings(settingsMap)
                ))
            );
            
            elasticsearchClient.indices().putSettings(updateSettingsRequest);
            logger.info("Index settings updated successfully with value: " + settingsMap.toString());
        } catch (Exception e) {
            logger.error("Failed to update index settings", e);
        }
    }

    public static SpeciesListItem convert(SpeciesListIndex index) {
        SpeciesListItem speciesListItem = new SpeciesListItem();
        speciesListItem.setId(new ObjectId(index.getId()));
        speciesListItem.setSpeciesListID(index.getSpeciesListID());
        speciesListItem.setSuppliedName(index.getSuppliedName());
        speciesListItem.setScientificName(index.getScientificName());
        speciesListItem.setVernacularName(index.getVernacularName());
        speciesListItem.setPhylum(index.getPhylum());
        speciesListItem.setClasss(index.getClasss());
        speciesListItem.setOrder(index.getOrder());
        speciesListItem.setFamily(index.getFamily());
        speciesListItem.setGenus(index.getGenus());
        speciesListItem.setTaxonID(index.getTaxonID());
        speciesListItem.setKingdom(index.getKingdom());
        speciesListItem.setProperties(index.getProperties());
        speciesListItem.setClassification(index.getClassification());
        speciesListItem.setDateCreated(parsedDate(index.getDateCreated()));
        speciesListItem.setLastUpdated(parsedDate(index.getLastUpdated()));
        speciesListItem.setLastUpdatedBy(index.getLastUpdatedBy());

        return speciesListItem;
    }

    private static Date parsedDate(String date) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<SpeciesListItem> convertList(List<SpeciesListIndex> list) {
        return list.stream().map(index -> convert(index)).collect(Collectors.toList());
    }

    public static BoolQuery.Builder buildQuery(
        String searchQuery,
        String speciesListID,
        String userId,
        Boolean isAdmin,
        Boolean isPrivate,
        List<Filter> filters,
        BoolQuery.Builder bq) {

        // Add common query logic
        addCommonQueryLogic(searchQuery, userId, isAdmin, isPrivate, bq);

        // Add speciesListID filter
        if (speciesListID != null) {
            bq.filter(f -> f.term(t -> t.field("speciesListID").value(speciesListID)));
        }

        // Add filters
        addFilters(filters, bq);

        return bq;
    }

    /**
     * Build a query specifically for searching species lists with prioritized
     * relevance scoring.
     * This method prioritizes matches in the speciesListName field over matches in
     * the general 'all' field.
     *
     * @param searchQuery The search query string
     * @param userId      The user ID for authorization filtering
     * @param isAdmin     Whether the user is an admin
     * @param isPrivate   Whether to search private lists
     * @param filters     Additional filters to apply
     * @param bq          The BoolQuery.Builder to build upon
     * @return The BoolQuery.Builder with the search query applied
     */
    public static BoolQuery.Builder buildListSearchQuery(
        String searchQuery,
        String userId,
        Boolean isAdmin,
        Boolean isPrivate,
        List<Filter> filters,
        BoolQuery.Builder bq) {

        // Add search query logic with prioritized relevance scoring
        addListSearchQueryLogic(searchQuery, userId, isAdmin, isPrivate, bq);

        // Add filters
        addFilters(filters, bq);

        return bq;
    }

    public static void buildQuery(
            String searchQuery,
            List<FieldValue> speciesListIDs,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            List<Filter> filters,
            BoolQuery.Builder bq) {

        // Add common query logic
        addCommonQueryLogic(searchQuery, userId, isAdmin, isPrivate, bq);

        // Add speciesListIDs filter
        if (speciesListIDs != null && !speciesListIDs.isEmpty()) {
            bq.filter(f -> f.terms(t -> t.field("speciesListID").terms(ta -> ta.value(speciesListIDs))));
        }

        // Add filters
        addFilters(filters, bq);
    }

    private static void addCommonQueryLogic(String searchQuery, String userId, Boolean isAdmin, Boolean isPrivate,
            BoolQuery.Builder bq) {
        // Add search query logic
        bq.should(m -> m.matchPhrase(mq -> mq.field("all").query(searchQuery.toLowerCase() + "*").boost(2.0f)));

        if (StringUtils.trimToNull(searchQuery) != null && searchQuery.length() > 1) {
            bq.minimumShouldMatch("1");
        }

        // Add userId filter for non-admin users and private lists
        if (userId != null || (!isAdmin && isPrivate != null && isPrivate)) {
            bq.filter(f -> f.term(t -> t.field("owner").value(userId)));
        }

        // Add isPrivate filter
        if (isPrivate != null) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(isPrivate)));
        }
    }

    /**
     * Add search query logic specifically for list searches with prioritized
     * relevance scoring.
     * This method prioritizes matches in the speciesListName field over matches in
     * the general 'all' field.
     */
    private static void addListSearchQueryLogic(String searchQuery, String userId, Boolean isAdmin, Boolean isPrivate,
            BoolQuery.Builder bq) {
        if (StringUtils.trimToNull(searchQuery) != null && searchQuery.length() > 1) {
            // Primary priority: exact match on speciesListName field
            bq.should(s -> s.matchPhrase(mp -> mp
                    .field("speciesListName.search")
                    .query(searchQuery.toLowerCase())
                    .boost(100.0f)));

            // Secondary priority: prefix match on speciesListName field
            bq.should(s -> s.prefix(p -> p
                    .field("speciesListName.keyword")
                    .value(searchQuery)
                    .boost(75.0f)));

            // Tertiary priority: fuzzy match on speciesListName field
            bq.should(s -> s.fuzzy(f -> f
                    .field("speciesListName.search")
                    .value(searchQuery.toLowerCase())
                    .fuzziness("AUTO")
                    .boost(50.0f)));

            // Lower priority: search across all fields (existing behavior)
            bq.should(s -> s.matchPhrase(mp -> mp
                    .field("all")
                    .query(searchQuery + "*")));

            bq.minimumShouldMatch("1");
        }

        // Add userId filter for non-admin users and private lists
        if (userId != null || (!isAdmin && isPrivate != null && isPrivate)) {
            bq.filter(f -> f.term(t -> t.field("owner").value(userId)));
        }

        // Add isPrivate filter
        if (isPrivate != null) {
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(isPrivate)));
        }
    }

    public static String getPropertiesFacetField(String filter) {
        if (CORE_BOOL_FIELDS.contains(filter)) {
            return filter;
        }
        if (CORE_FIELDS.contains(filter)) {
            return filter + ".keyword";
        }
        if (filter.startsWith("classification.")) {
            return filter + ".keyword";
        }
        return "properties." + filter + ".keyword";
    }

    private static void addFilters(List<Filter> filters, BoolQuery.Builder bq) {
        if (filters != null && !filters.isEmpty()) {
            // Group filters by key
            Map<String, List<Filter>> filtersByKey = filters.stream()
                .collect(Collectors.groupingBy(Filter::getKey));

            // For each key, create a sub-bool query with OR logic
            filtersByKey.forEach((key, filtersForKey) -> {
                // For handling properties filters specifically
                if (key.startsWith("properties.")) {
                    String propertyField = key.substring("properties.".length());
                    Filter filter = filtersForKey.get(0);
                    List<String> values = filtersForKey.stream().map(Filter::getValue).toList();
                    // Add nested query for properties
                    bq.must(m -> m.nested(n -> n
                        .path("properties")
                        .query(nq -> nq
                            .bool(nbq -> nbq
                                .must(nm -> nm.term(t -> t.field("properties.key.keyword").value(propertyField)))
                                .must(nm -> nm.term(t -> t.field("properties.value.keyword").value(filter.getValue())))
                            )
                        )
                        .query(q -> q.bool(propBq -> {
                            // First match the property key
                            propBq.must(pm -> pm.term(pt -> pt.field("properties.key.keyword").value(propertyField)));

                            // Then match any of the values (OR)
                            if (values.size() == 1) {
                                propBq.must(pm -> pm.term(pt -> pt.field("properties.value.keyword").value(filter.getValue())));
                            } else {
                                propBq.must(pm -> pm.bool(valuesBq -> {
                                    for (String value : values) {
                                        valuesBq.should(s -> s.term(t -> t.field("properties.value.keyword").value(value)));
                                    }
                                    return valuesBq;
                                }));
                            }
                            return propBq;
                        }))
                    ));
                }
                // Handle other filters as normal
                else {
                    // Existing filter handling code
                    bq.must(keyQuery ->
                        keyQuery.bool(keyBool -> {
                            if (filtersForKey.size() == 1) {
                                // Single filter for this key
                                Filter filter = filtersForKey.get(0);
                                keyBool.must(m -> m.term(t -> t.field(getPropertiesFacetField(filter.getKey())).value(filter.getValue())));
                            } else {
                                // Multiple filters with OR logic
                                filtersForKey.forEach(filter ->
                                    keyBool.should(m -> m.term(t -> t.field(getPropertiesFacetField(filter.getKey())).value(filter.getValue())))
                                );
                                keyBool.minimumShouldMatch("1");
                            }
                            return keyBool;
                        })
                    );
                }
            });
        }
    }

    @NotNull
    public static String cleanRawQuery(String searchQuery) {
        if (searchQuery != null) return searchQuery.trim().replace("\"", "\\\"");
        return "";
    }

    /**
     * Filter results based on "query" being present in the provided fields param.
     *
     * @param searchQuery      The search query string.
     * @param restrictedFields The fields to restrict the search to.
     * @param mainBq           The main BoolQuery.Builder to which the restrictions
     *                         will be added.
     */
    public static void restrictFields(String searchQuery, HashSet<String> restrictedFields, BoolQuery.Builder mainBq) {
        String search = cleanRawQuery(searchQuery);

        if (restrictedFields == null || restrictedFields.isEmpty() || search.trim().isEmpty()) {
            return;
        }

        // Create a single outer bool query that will have should clauses for each field
        // but will itself be a must clause at the top level
        BoolQuery.Builder outerDisjunctionBq = new BoolQuery.Builder();

        // Process each restricted field as a separate should clause
        for (String field : restrictedFields) {
            if (TOP_LEVEL_SEARCHABLE_FIELDS.contains(field)) {
                String actualFieldToSearch = field + ".search";

                // Add this field as a should clause
                outerDisjunctionBq.should(s ->
                        s.matchPhrase(mp -> mp.field(actualFieldToSearch).query(search))
                );
            } else {
                // For nested properties
                outerDisjunctionBq.should(s -> s.nested(n -> n
                        .path("properties")
                        .scoreMode(ChildScoreMode.Avg)
                        .query(nq -> nq.bool(nb -> {
                            // Key must match
                            nb.must(m1 -> m1.term(t -> t
                                    .field("properties.key")
                                    .value(field)));

                            // Value must match exactly
                            nb.must(m2 -> m2.matchPhrase(mp -> mp
                                    .field("properties.value")
                                    .query(search)));

                            return nb;
                        }))));
            }
        }

        // Set minimum_should_match to 1 to ensure at least one field must match
        outerDisjunctionBq.minimumShouldMatch("1");

        // Wrap the field disjunction in a must clause at the top level
        mainBq.must(m -> m.bool(outerDisjunctionBq.build()));
    }
}
