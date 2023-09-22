package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.FacetCount;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.Image;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.Release;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.ReleaseMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.TaxonService;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

/** GraphQL API for lists */
@Controller("/graphql")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GraphQLController {

  @Value("${image.url}")
  private String imageTemplateUrl;

  @Value("${bie.url}")
  private String bieTemplateUrl;

  public static final String SPECIES_LIST_ID = "speciesListID";
  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired protected ReleaseMongoRepository releaseMongoRepository;
  @Autowired protected ElasticsearchOperations elasticsearchOperations;

  @Autowired protected TaxonService taxonService;

  public static SpeciesListItem convert(SpeciesListIndex index) {
    SpeciesListItem speciesListItem = new SpeciesListItem();
    speciesListItem.setSpeciesListID(index.getSpeciesListID());
    speciesListItem.setScientificName(index.getScientificName());
    List<KeyValue> keyValues = new ArrayList<>();
    index.getProperties().entrySet().stream()
        .forEach(e -> keyValues.add(new KeyValue(e.getKey(), e.getValue())));
    speciesListItem.setId(index.getId());
    speciesListItem.setProperties(keyValues);
    speciesListItem.setClassification(index.getClassification());
    return speciesListItem;
  }

  public static List<SpeciesListItem> convertList(List<SpeciesListIndex> list) {
    return list.stream().map(index -> convert(index)).collect(Collectors.toList());
  }

  /**
   * Search across lists
   *
   * @param searchQuery
   * @param page
   * @param size
   * @param userId
   * @param isPrivate
   * @return Page of SpeciesList
   */
  @QueryMapping
  public Page<SpeciesList> lists(
      @Argument String searchQuery,
      @Argument Integer page,
      @Argument Integer size,
      @Argument String userId,
      @Argument Boolean isPrivate,
      @AuthenticationPrincipal Principal principal) {

    // if searching private lists, check user is authorized
    if (isPrivate && !AuthUtils.isAuthorized(principal)) {
      return null;
    }

    NativeQueryBuilder builder = NativeQuery.builder().withPageable(PageRequest.of(1, 1));
    builder.withQuery(
        q ->
            q.bool(
                bq -> {
                  buildQuery(
                      cleanRawQuery(searchQuery), null, userId, isPrivate, new ArrayList<>(), bq);
                  return bq;
                }));

    // aggregation on species list ID
    builder.withAggregation(
        SPECIES_LIST_ID,
        Aggregation.of(a -> a.terms(ta -> ta.field(SPECIES_LIST_ID + ".keyword").size(1000))));

    Query aggQuery = builder.build();

    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(aggQuery, SpeciesListIndex.class);

    ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();
    ElasticsearchAggregation agg1 = agg.aggregations().get(0); // get(SPECIES_LIST_ID);
    List<StringTermsBucket> array = agg1.aggregation().getAggregate().sterms().buckets().array();

    Map<String, Long> speciesListIDs =
        array.stream()
            .skip(page * size)
            .limit(size)
            .collect(
                Collectors.toMap(
                    bucket -> bucket.key().stringValue(), bucket -> bucket.docCount()));

    Iterable<SpeciesList> speciesLists =
        speciesListMongoRepository.findAllById(speciesListIDs.keySet());
    for (SpeciesList speciesList : speciesLists) {
      speciesList.setRowCount(speciesListIDs.get(speciesList.getId()).intValue());
    }

    List<SpeciesList> result = new ArrayList<>();
    speciesLists.forEach(result::add);

    return new PageImpl<>(result, PageRequest.of(page, size), array.size());
  }

  private static BoolQuery.Builder buildQuery(
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
      bq.filter(f -> f.term(t -> t.field("owner").value(userId)));
    } else if (isPrivate != null) {
      bq.filter(f -> f.term(t -> t.field("isPrivate").value(isPrivate)));
    } else if (speciesListID != null) {
      bq.filter(f -> f.term(t -> t.field("speciesListID").value(speciesListID)));
    }

    if (filters != null) {
      filters.forEach(
          filter ->
              bq.filter(
                  f ->
                      f.queryString(
                          qs ->
                              qs.defaultOperator(Operator.And)
                                  .fields(getPropertiesFacetField(filter.getKey()))
                                  .query(filter.getValue()))));
    }

    return bq;
  }

  @QueryMapping
  public Facet listsFacet() {
    Facet facet = new Facet();
    facet.setKey("listTypes");
    facet.setCounts(new ArrayList<>());
    facet
        .getCounts()
        .add(
            new FacetCount(
                "authoritative",
                (long) speciesListMongoRepository.countSpeciesListByIsAuthoritative(true)));
    facet
        .getCounts()
        .add(
            new FacetCount(
                "private", (long) speciesListMongoRepository.countSpeciesListByIsPrivate(true)));
    facet.getCounts().add(new FacetCount("total", (long) speciesListMongoRepository.count()));
    return facet;
  }

  @QueryMapping
  public Page<Release> listReleases(
      @Argument String speciesListID, @Argument Integer page, @Argument Integer size) {
    Pageable pageable = PageRequest.of(page, size);
    return releaseMongoRepository.findBySpeciesListID(speciesListID, pageable);
  }

  @QueryMapping
  public SpeciesList getSpeciesListMetadata(@Argument String speciesListID) {
    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findById(speciesListID);
    if (optionalSpeciesList.isPresent()) {
      return optionalSpeciesList.get();
    }
    return null;
  }

  @QueryMapping
  public Page<SpeciesListItem> getSpeciesList(
      @Argument String speciesListID, @Argument Integer page, @Argument Integer size) {
    return filterSpeciesList(speciesListID, null, new ArrayList<>(), page, size);
  }

  @SchemaMapping(typeName = "Mutation", field = "updateMetadata")
  public SpeciesList updateMetadata(
      @Argument String id,
      @Argument String title,
      @Argument String description,
      @Argument String licence,
      @Argument String listType,
      @Argument String authority,
      @Argument String region,
      @Argument String wkt,
      @Argument Boolean isPrivate,
      @Argument Boolean isThreatened,
      @Argument Boolean isInvasive,
      @Argument Boolean isAuthoritative,
      @Argument Boolean isSDS,
      @Argument Boolean isBIE,
      @AuthenticationPrincipal Principal principal) {
    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(id);
    if (speciesList.isEmpty()) {
      return null;
    }

    if (AuthUtils.isAuthorized(speciesList.get(), principal)) {
      boolean reindexRequired = false;

      SpeciesList toUpdate = speciesList.get();
      if (title != null && !title.equalsIgnoreCase(toUpdate.getTitle())
          || listType != null && !listType.equalsIgnoreCase(toUpdate.getListType())
          || isPrivate != null && !isPrivate.equals(toUpdate.getIsPrivate())) {
        reindexRequired = true;
      }

      toUpdate.setTitle(title);
      toUpdate.setDescription(description);
      toUpdate.setLicence(licence);
      toUpdate.setListType(listType);
      toUpdate.setAuthority(authority);
      toUpdate.setRegion(region);
      toUpdate.setIsPrivate(isPrivate);
      toUpdate.setIsThreatened(isThreatened);
      toUpdate.setIsInvasive(isInvasive);
      toUpdate.setIsAuthoritative(isAuthoritative);
      toUpdate.setIsBIE(isBIE);
      toUpdate.setIsSDS(isSDS);
      toUpdate.setWkt(wkt);
      toUpdate.setLastUpdatedBy(principal.getName());

      // If the visibility has changed, update the visibility of the list items
      // in elasticsearch and mongo
      SpeciesList updatedList = speciesListMongoRepository.save(toUpdate);
      if (reindexRequired) {
        taxonService.reindex(updatedList.getId());
      }

      return updatedList;
    }
    return null;
  }

  @QueryMapping
  public Page<SpeciesListItem> filterSpeciesList(
      @Argument String speciesListID,
      @Argument String searchQuery,
      @Argument List<Filter> filters,
      @Argument Integer page,
      @Argument Integer size) {

    Pageable pageableRequest = PageRequest.of(page, size);
    NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
    builder.withQuery(
        q ->
            q.bool(
                bq -> {
                  buildQuery(cleanRawQuery(searchQuery), speciesListID, null, null, filters, bq);
                  return bq;
                }));

    Query query = builder.build();
    query.setPageable(pageableRequest);
    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(
            query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

    List<SpeciesListItem> speciesLists =
        convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));
    return new PageImpl<>(speciesLists, pageableRequest, results.getTotalHits());
  }

  @NotNull
  private static String cleanRawQuery(String searchQuery) {
    if (searchQuery != null) return searchQuery.trim().replace("\"", "\\\"");
    return "";
  }

  @QueryMapping
  public List<Facet> facetSpeciesList(
      @Argument String speciesListID,
      @Argument String searchQuery,
      @Argument List<Filter> filters,
      @Argument List<String> facetFields,
      @Argument Integer page,
      @Argument Integer size) {

    // get facet fields unique to this list
    if (facetFields == null || facetFields.isEmpty()) {
      Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListID);
      if (speciesList.isEmpty()) {
        return null;
      }
      facetFields = speciesList.get().getFacetList();
    }

    // retrieve field list from species_list
    NativeQueryBuilder builder = NativeQuery.builder();
    builder.withQuery(
        q ->
            q.bool(
                bq -> {
                  buildQuery(cleanRawQuery(searchQuery), speciesListID, null, null, filters, bq);
                  return bq;
                }));

    if (facetFields != null) {
      // add filter to query
      for (String facetField : facetFields) {
        builder.withAggregation(
            facetField,
            Aggregation.of(
                a -> a.terms(ta -> ta.field(getPropertiesFacetField(facetField)).size(10))));
      }
    }

    List<String> classificationFields = new ArrayList<>();
    classificationFields.add("classification.genus");
    classificationFields.add("classification.family");
    classificationFields.add("classification.order");
    classificationFields.add("classification.class");
    classificationFields.add("classification.phylum");
    classificationFields.add("classification.kingdom");

    for (String classificationField : classificationFields) {
      builder.withAggregation(
          classificationField,
          Aggregation.of(a -> a.terms(ta -> ta.field(classificationField + ".keyword").size(10))));
    }

    Query aggQuery = builder.build();
    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(aggQuery, SpeciesListIndex.class);

    ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();

    List<Facet> facets = new ArrayList<>();
    if (facetFields != null) {
      facetFields.addAll(classificationFields);
      facetFields.forEach(
          facetField -> {
            ElasticsearchAggregation agg1 =
                agg.aggregations().stream()
                    .filter(
                        elasticsearchAggregation ->
                            elasticsearchAggregation.aggregation().getName().equals(facetField))
                    .findFirst()
                    .get();
            List<StringTermsBucket> array =
                agg1.aggregation().getAggregate().sterms().buckets().array();
            Facet facet = new Facet();
            facet.setCounts(new ArrayList<>());
            facet.setKey(facetField);
            array.forEach(
                bucket -> {
                  facet
                      .getCounts()
                      .add(new FacetCount(bucket.key().stringValue(), bucket.docCount()));
                });
            facets.add(facet);
          });
    }

    return facets;
  }

  @QueryMapping
  public Image getTaxonImage(@Argument String taxonID) throws Exception {
    // get taxon image from BIE
    // https://bie.ala.org.au/ws/taxon/https://id.biodiversity.org.au/node/apni/2910201
    Map<String, Object> bieJson = loadJson(String.format(bieTemplateUrl, taxonID));
    if (bieJson != null) {
      String imageID = (String) bieJson.getOrDefault("imageIdentifier", null);
      if (imageID != null) {
        return new Image(String.format(imageTemplateUrl, imageID));
      }
    }
    return null;
  }

  public Map<String, Object> loadJson(String url) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(new URL(url), Map.class);
  }

  private static String getPropertiesFacetField(String filter) {
    if (filter.startsWith("classification.")) {
      return filter + ".keyword";
    }
    return "properties." + filter + ".keyword";
  }
}
