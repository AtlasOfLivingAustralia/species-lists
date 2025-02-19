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
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
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
        List<KeyValue> keyValues = new ArrayList<>();
        index.getProperties().entrySet().stream()
                .forEach(e -> keyValues.add(new KeyValue(e.getKey(), e.getValue())));
        speciesListItem.setProperties(keyValues);
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

        bq.should(
                m ->
                        m.matchPhrase(
                                mq -> mq.field("all").query(searchQuery.toLowerCase() + "*").boost(2.0f)));

        if (StringUtils.trimToNull(searchQuery) != null && searchQuery.length() > 1) {
            bq.minimumShouldMatch("1");
        }

        if (userId != null) {
            // return all lists for this user
            bq.filter(f -> f.term(t -> t.field("owner").value(userId)));
        }
        if (isPrivate != null) {
            // return all private lists
            bq.filter(f -> f.term(t -> t.field("isPrivate").value(isPrivate)));
        }
        if (speciesListID != null) {
            // return this one list
            bq.filter(f -> f.term(t -> t.field("speciesListID").value(speciesListID)));
        }

        if (filters != null) {
            filters.forEach(filter -> addFilter(filter, bq));
        }
        return bq;
    }

    public static String getPropertiesFacetField(String filter) {
        if (CORE_FIELDS.contains(filter)) {
            return filter + ".keyword";
        }
        if (filter.startsWith("classification.")) {
            return filter + ".keyword";
        }
        return "properties." + filter + ".keyword";
    }

    public static void addFilter(Filter filter, BoolQuery.Builder bq) {
        if (!CORE_BOOL_FIELDS.contains(filter.getKey())) {
            bq.filter(
                    f ->
                            f.queryString(
                                    qs ->
                                            qs.defaultOperator(Operator.And)
                                                    .fields(getPropertiesFacetField(filter.getKey()))
                                                    .query(filter.getValue())));

        } else {
            bq.filter(f -> f.term(t -> t.field(filter.getKey()).value(filter.getValue())));
        }
    }

    @NotNull
    public static String cleanRawQuery(String searchQuery) {
        if (searchQuery != null) return searchQuery.trim().replace("\"", "\\\"");
        return "";
    }
}
