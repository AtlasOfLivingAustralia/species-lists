package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.ReleaseMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.MetadataService;
import au.org.ala.listsapi.service.ValidationService;
import au.org.ala.listsapi.service.TaxonService;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.*;
import graphql.schema.DataFetchingEnvironment;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URL;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

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
                    "tags");

    public static final List<String> CORE_BOOL_FIELDS =
            List.of("isBIE", "isAuthoritative", "hasRegion", "isSDS");

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

    static Date parsedDate(String date) {
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
        Boolean isPrivate,
        List<Filter> filters,
        BoolQuery.Builder bq) {

        // Add common query logic
        addCommonQueryLogic(searchQuery, userId, isPrivate, bq);

        // Add speciesListID filter
        if (speciesListID != null) {
            bq.filter(f -> f.term(t -> t.field("speciesListID").value(speciesListID)));
        }

        // Add filters
        addFilters(filters, bq);

        return bq;
    }

    public static void buildQuery(
        String searchQuery,
        List<FieldValue> speciesListIDs,
        String userId,
        Boolean isPrivate,
        List<Filter> filters,
        BoolQuery.Builder bq) {

        // Add common query logic
        addCommonQueryLogic(searchQuery, userId, isPrivate, bq);

        // Add speciesListIDs filter
        if (speciesListIDs != null && !speciesListIDs.isEmpty()) {
            bq.filter(f -> f.terms(t -> t.field("speciesListID").terms(ta -> ta.value(speciesListIDs))));
        }

        // Add filters
        addFilters(filters, bq);
    }

    private static void addCommonQueryLogic(String searchQuery, String userId, Boolean isPrivate, BoolQuery.Builder bq) {
        // Add search query logic
        bq.should(m -> m.matchPhrase(mq -> mq.field("all").query(searchQuery.toLowerCase() + "*").boost(2.0f)));

        if (StringUtils.trimToNull(searchQuery) != null && searchQuery.length() > 1) {
            bq.minimumShouldMatch("1");
        }

        // Add userId filter
        if (userId != null) {
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
            });
        }
    }

    @NotNull
    public static String cleanRawQuery(String searchQuery) {
        if (searchQuery != null) return searchQuery.trim().replace("\"", "\\\"");
        return "";
    }

    public static void restrictFields(String searchQuery, HashSet<String> restrictedFields, BoolQuery.Builder bq) {
        String search = cleanRawQuery(searchQuery);

        for (String field : restrictedFields) {
            if (TOP_LEVEL_SEARCHABLE_FIELDS.contains(field)) {
                // E.g. if userField == "scientificName", the actual field to match is "scientificName.search"
                String actualFieldToSearch = field + ".search";

                bq.must(m ->
                        m.match(mq -> mq.field(actualFieldToSearch).query(
                                search
                        ))
                );

            } else {
                // Otherwise, treat it as a nested property
                // This means userField is actually the 'key' in properties.key
                // and we do a match on properties.value for searchText
                bq.must(s -> s.nested(n -> n
                        .path("properties")
                        .scoreMode(ChildScoreMode.Avg)
                        .query(nq -> nq.bool(nb -> {
                            nb.should(m1 -> m1.term(t -> t
                                    .field("properties.key")
                                    .value(field)));
                            nb.must(m2 -> m2.match(mt -> mt
                                    .field("properties.value")
                                    .query(search)));
                            return nb;
                        }))
                ));
            }
        }
    }
}
