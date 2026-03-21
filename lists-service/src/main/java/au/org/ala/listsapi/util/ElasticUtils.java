/**
 * Copyright (c) 2025 Atlas of Living Australia All Rights Reserved.
 *
 * <p>The contents of this file are subject to the Mozilla Public License Version 1.1 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 */
package au.org.ala.listsapi.util;

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
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

/** GraphQL API for lists */
@Component
public class ElasticUtils {
    public static final List<String> CORE_FIELDS = ElasticsearchQueryBuilder.CORE_FIELDS;

    public static final List<String> CORE_BOOL_FIELDS = ElasticsearchQueryBuilder.CORE_BOOL_FIELDS;

    public static final String SPECIES_LIST_ID = "speciesListID";
    @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
    @Autowired protected ReleaseMongoRepository releaseMongoRepository;
    @Autowired protected ElasticsearchOperations elasticsearchOperations;
    @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
    @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @Autowired protected TaxonService taxonService;
    @Autowired protected ValidationService validationService;
    @Autowired protected AuthUtils authUtils;
    @Autowired protected MetadataService metadataService;

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
        return ElasticsearchQueryBuilder.buildQuery(
                searchQuery, speciesListID, userId, isAdmin, isPrivate, filters, bq);
    }

    /**
     * Build a query specifically for searching species lists with prioritized relevance scoring.
     * This method prioritizes matches in the speciesListName field over matches in the general
     * 'all' field.
     *
     * @param searchQuery The search query string
     * @param userId The user ID for authorization filtering
     * @param isAdmin Whether the user is an admin
     * @param isPrivate Whether to search private lists
     * @param filters Additional filters to apply
     * @param bq The BoolQuery.Builder to build upon
     * @return The BoolQuery.Builder with the search query applied
     */
    public static BoolQuery.Builder buildListSearchQuery(
            String searchQuery,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            List<Filter> filters,
            BoolQuery.Builder bq) {
        return ElasticsearchQueryBuilder.buildListSearchQuery(
                searchQuery, userId, isAdmin, isPrivate, filters, bq);
    }

    public static void buildQuery(
            String searchQuery,
            List<FieldValue> speciesListIDs,
            String userId,
            Boolean isAdmin,
            Boolean isPrivate,
            List<Filter> filters,
            BoolQuery.Builder bq) {
        ElasticsearchQueryBuilder.buildQuery(
                searchQuery, speciesListIDs, userId, isAdmin, isPrivate, filters, bq);
    }

    /**
     * Adds a new filter or updates an existing filter with the same key
     *
     * @param filters The list of filters to modify
     * @param newFilter The filter to add or update
     * @return The updated list of filters
     */
    public static List<Filter> addOrUpdateFilter(List<Filter> filters, Filter newFilter) {
        if (filters == null) {
            filters = new ArrayList<>();
        }

        // Remove any existing filter with the same key
        filters.removeIf(f -> f.getKey().equals(newFilter.getKey()));

        // Add the new filter
        filters.add(newFilter);

        return filters;
    }

    public static String getPropertiesFacetField(String filter) {
        return ElasticsearchQueryBuilder.getPropertiesFacetField(filter);
    }

    @NotNull
    public static String cleanRawQuery(String searchQuery) {
        return ElasticsearchQueryBuilder.cleanRawQuery(searchQuery);
    }

    /**
     * Filter results based on "query" being present in the provided fields param.
     *
     * @param searchQuery The search query string.
     * @param restrictedFields The fields to restrict the search to.
     * @param mainBq The main BoolQuery.Builder to which the restrictions will be added.
     */
    public static void restrictFields(
            String searchQuery, HashSet<String> restrictedFields, BoolQuery.Builder mainBq) {
        ElasticsearchQueryBuilder.restrictFields(searchQuery, restrictedFields, mainBq);
    }
}
