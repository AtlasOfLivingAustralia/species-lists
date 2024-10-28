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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

/** GraphQL API for lists */
@Controller
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "GraphQL", description = "GraphQL Services for species lists lookups")
public class GraphQLController {

  private static final Logger logger = LoggerFactory.getLogger(GraphQLController.class);

  @Value("${image.url}")
  private String imageTemplateUrl;

  @Value("${bie.url}")
  private String bieTemplateUrl;

  @Value("${bie.images.url}")
  private String bieImagesTemplateUrl;

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
  @Autowired private SpeciesListItemMongoRepository speciesListItemMongoRepository;

  @Autowired protected TaxonService taxonService;
  @Autowired protected ValidationService validationService;
  @Autowired protected AuthUtils authUtils;
  @Autowired protected MetadataService metadataService;

  public static SpeciesListItem convert(SpeciesListIndex index) {

    SpeciesListItem speciesListItem = new SpeciesListItem();
    speciesListItem.setId(index.getId());
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
      @Argument List<Filter> filters,
      @Argument Integer page,
      @Argument Integer size,
      @Argument String userId,
      @Argument Boolean isPrivate,
      @AuthenticationPrincipal Principal principal) {

    // if searching private lists, check user is authorized
    if (isPrivate && !authUtils.isAuthorized(principal)) {
      logger.info("User not authorized to private access lists");
      throw new AccessDeniedException("You dont have access to this list");
    }

    NativeQueryBuilder builder = NativeQuery.builder().withPageable(PageRequest.of(1, 1));
    builder.withQuery(
        q ->
            q.bool(
                bq -> {
                  buildQuery(cleanRawQuery(searchQuery), null, userId, isPrivate, filters, bq);
                  return bq;
                }));

    builder.withAggregation(
        "types_count",
        Aggregation.of(a -> a.cardinality(ca -> ca.field(SPECIES_LIST_ID + ".keyword"))));

    // aggregation on species list ID
    builder.withAggregation(
        SPECIES_LIST_ID,
        Aggregation.of(a -> a.terms(ta -> ta.field(SPECIES_LIST_ID + ".keyword").size(10000))));

    Query aggQuery = builder.build();

    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(aggQuery, SpeciesListIndex.class);

    ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();

    ElasticsearchAggregation cardinality = agg.aggregations().get(0);
    Long noOfLists = cardinality.aggregation().getAggregate().cardinality().value();

    ElasticsearchAggregation agg1 = agg.aggregations().get(1);
    List<StringTermsBucket> array = agg1.aggregation().getAggregate().sterms().buckets().array();

    Map<String, Long> speciesListIDs =
        array.stream()
            .skip(page * size)
            .limit(size)
            .collect(
                Collectors.toMap(
                    bucket -> bucket.key().stringValue(), bucket -> bucket.docCount()));

    // lookup species list metadata
    Iterable<SpeciesList> speciesLists =
        speciesListMongoRepository.findAllById(speciesListIDs.keySet());
    for (SpeciesList speciesList : speciesLists) {
      speciesList.setRowCount(speciesListIDs.get(speciesList.getId()).intValue());
    }

    List<SpeciesList> result = new ArrayList<>();
    speciesLists.forEach(result::add);

    return new PageImpl<>(result, PageRequest.of(page, size), noOfLists);
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
      // return all lists for this user
      bq.filter(f -> f.term(t -> t.field("owner").value(userId)));
    } else if (isPrivate != null) {
      // return all private lists
      bq.filter(f -> f.term(t -> t.field("isPrivate").value(isPrivate)));
    } else if (speciesListID != null) {
      // return this one list
      bq.filter(f -> f.term(t -> t.field("speciesListID").value(speciesListID)));
    }

    if (filters != null) {
      filters.forEach(filter -> addFilter(filter, bq));
    }
    return bq;
  }

  static void addFilter(Filter filter, BoolQuery.Builder bq) {
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

  @GraphQlExceptionHandler
  public GraphQLError handle(@NonNull Throwable ex, @NonNull DataFetchingEnvironment environment){
    return GraphQLError
            .newError()
            .errorType(ErrorType.ValidationError)
            .message(ex.getMessage())
            .path(environment.getExecutionStepInfo().getPath())
            .location(environment.getField().getSourceLocation())
            .build();
  }

  @QueryMapping
  public Page<Release> listReleases(
      @Argument String speciesListID, @Argument Integer page, @Argument Integer size) {
    Pageable pageable = PageRequest.of(page, size);
    return releaseMongoRepository.findBySpeciesListID(speciesListID, pageable);
  }

  @QueryMapping
  public SpeciesList getSpeciesListMetadata(
      @Argument String speciesListID, @AuthenticationPrincipal Principal principal) {
    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findById(speciesListID);
    if (optionalSpeciesList.isPresent()) {
      Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListID);
      if (speciesList.isEmpty()) {
        return null;
      }

      // private list, check user is authorized
      if (speciesList.get().getIsPrivate()
          && !authUtils.isAuthorized(speciesList.get(), principal)) {
        logger.info("User not authorized to private access list: " + speciesListID);
        throw new AccessDeniedException("You dont have access to this list");
      }

      return optionalSpeciesList.get();
    }
    return null;
  }

  @QueryMapping
  public Page<SpeciesListItem> getSpeciesList(
      @Argument String speciesListID,
      @Argument Integer page,
      @Argument Integer size,
      @AuthenticationPrincipal Principal principal) {
    return filterSpeciesList(
        speciesListID, null, new ArrayList<>(), page, size, null, null, principal);
  }

  @SchemaMapping(typeName = "Mutation", field = "addField")
  public SpeciesList addField(
      @Argument String id,
      @Argument String fieldName,
      @Argument String fieldValue,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(id);
    if (speciesList.isEmpty()) {
      return null;
    }

    if (!authUtils.isAuthorized(speciesList.get(), principal)) {
      logger.info("User not authorized to modify access list: " + id);
      throw new AccessDeniedException("You dont have authorisation to modify this list");
    }

    SpeciesList toUpdate = speciesList.get();
    toUpdate.getFieldList().add(fieldName);

    if (StringUtils.isNotEmpty(fieldValue)) {
      int startIndex = 0;
      int pageSize = 1000;
      Pageable pageable = PageRequest.of(startIndex, pageSize);

      boolean finished = false;
      while (!finished) {
        Page<SpeciesListItem> page =
            speciesListItemMongoRepository.findBySpeciesListID(id, pageable);
        List<SpeciesListItem> toSave = new ArrayList<>();
        for (SpeciesListItem item : page) {
          item.getProperties().add(new KeyValue(fieldName, fieldValue));
          toSave.add(item);
        }

        speciesListItemMongoRepository.saveAll(toSave);

        if (page.isEmpty()) {
          finished = true;
        } else {
          startIndex += 1;
          pageable = PageRequest.of(startIndex, pageSize);
        }
      }
      // reindex
      taxonService.reindex(id);
    }

    return speciesListMongoRepository.save(toUpdate);
  }

  @SchemaMapping(typeName = "Mutation", field = "renameField")
  public SpeciesList renameField(
      @Argument String id,
      @Argument String oldName,
      @Argument String newName,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(id);
    if (speciesList.isEmpty()) {
      return null;
    }

    if (!authUtils.isAuthorized(speciesList.get(), principal)) {
      logger.info("User not authorized to modify access list: " + id);
      throw new AccessDeniedException("You dont have access to this list");
    }

    // remove from species list metadata
    SpeciesList toUpdate = speciesList.get();
    toUpdate.getFieldList().remove(oldName);
    toUpdate.getFieldList().add(newName);

    int startIndex = 0;
    int pageSize = 1000;
    Pageable pageable = PageRequest.of(startIndex, pageSize);
    boolean finished = false;

    while (!finished) {
      Page<SpeciesListItem> page = speciesListItemMongoRepository.findBySpeciesListID(id, pageable);
      List<SpeciesListItem> toSave = new ArrayList<>();
      for (SpeciesListItem item : page) {

        Optional<KeyValue> kv =
            item.getProperties().stream().filter(k -> k.getKey().equals(oldName)).findFirst();
        if (kv.isPresent()) {
          kv.get().setKey(newName);
          toSave.add(item);
        }
      }

      speciesListItemMongoRepository.saveAll(toSave);

      if (page.isEmpty()) {
        finished = true;
      } else {
        startIndex += 1;
        pageable = PageRequest.of(startIndex, pageSize);
      }
    }
    // reindex
    taxonService.reindex(id);

    return speciesListMongoRepository.save(toUpdate);
  }

  @SchemaMapping(typeName = "Mutation", field = "removeField")
  public SpeciesList removeField(
      @Argument String id,
      @Argument String fieldName,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(id);
    if (speciesList.isEmpty()) {
      return null;
    }

    if (!authUtils.isAuthorized(speciesList.get(), principal)) {
      logger.info("User not authorized to modify access list: " + id);
      throw new AccessDeniedException("You dont have access to this list");
    }

    SpeciesList toUpdate = speciesList.get();
    toUpdate.getFieldList().remove(fieldName);

    int startIndex = 0;
    int pageSize = 1000;
    Pageable pageable = PageRequest.of(startIndex, pageSize);

    boolean finished = false;
    while (!finished) {
      Page<SpeciesListItem> page = speciesListItemMongoRepository.findBySpeciesListID(id, pageable);
      List<SpeciesListItem> toSave = new ArrayList<>();
      for (SpeciesListItem item : page) {

        Optional<KeyValue> kv =
            item.getProperties().stream().filter(k -> k.getKey().equals(fieldName)).findFirst();
        if (kv.isPresent()) {
          item.getProperties().remove(kv.get());
          toSave.add(item);
        }
      }
      speciesListItemMongoRepository.saveAll(toSave);

      if (page.isEmpty()) {
        finished = true;
      } else {
        startIndex += 1;
        pageable = PageRequest.of(startIndex, pageSize);
      }
    }

    // reindex
    taxonService.reindex(id);

    return speciesListMongoRepository.save(toUpdate);
  }

  @SchemaMapping(typeName = "Mutation", field = "updateSpeciesListItem")
  public SpeciesListItem updateSpeciesListItem(
      @Argument InputSpeciesListItem inputSpeciesListItem,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesListItem> optionalSpeciesListItem =
        speciesListItemMongoRepository.findById(inputSpeciesListItem.getId());
    if (optionalSpeciesListItem.isEmpty()) {
      return null;
    }

    Optional<SpeciesList> optionalSpeciesList =
        speciesListMongoRepository.findById(inputSpeciesListItem.getSpeciesListID());

    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    if (!authUtils.isAuthorized(optionalSpeciesList.get(), principal)) {
      logger.info(
          "User not authorized to modify access list: " + optionalSpeciesList.get().getId());
      throw new AccessDeniedException("You dont have access to this list");
    }

    SpeciesList speciesList = optionalSpeciesList.get();
    SpeciesListItem speciesListItem = optionalSpeciesListItem.get();
    updateItem(inputSpeciesListItem, speciesListItem, principal);

    // update last updated
    updateLastUpdated(speciesListItem, principal);

    // rematch taxonomy
    try {
      Classification classification = taxonService.lookupTaxon(speciesListItem);
      speciesListItem.setClassification(classification);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }

    // reindex the item
    reindex(speciesListItem, speciesList);

    logger.info("Updated species list item: " + speciesListItem.getId());
    return speciesListItem;
  }

  private SpeciesListItem updateItem(
      InputSpeciesListItem inputSpeciesListItem, SpeciesListItem speciesListItem, Principal principal) {
    speciesListItem.setScientificName(inputSpeciesListItem.getScientificName());
    speciesListItem.setTaxonID(inputSpeciesListItem.getTaxonID());
    speciesListItem.setGenus(inputSpeciesListItem.getGenus());
    speciesListItem.setFamily(inputSpeciesListItem.getFamily());
    speciesListItem.setOrder(inputSpeciesListItem.getOrder());
    speciesListItem.setClasss(inputSpeciesListItem.getClasss());
    speciesListItem.setPhylum(inputSpeciesListItem.getPhylum());
    speciesListItem.setKingdom(inputSpeciesListItem.getKingdom());
    speciesListItem.setVernacularName(inputSpeciesListItem.getVernacularName());
    speciesListItem.setProperties(
        inputSpeciesListItem.getProperties().stream()
            .map(kv -> new KeyValue(kv.getKey(), kv.getValue()))
            .collect(Collectors.toList()));
    speciesListItem.setLastUpdated(new Date());
    speciesListItem.setLastUpdatedBy(principal.getName());
    if (speciesListItem.getSpeciesListID() == null) {
      speciesListItem.setSpeciesListID(inputSpeciesListItem.getSpeciesListID());
    }
    return speciesListItemMongoRepository.save(speciesListItem);
  }

  private void reindex(SpeciesListItem speciesListItem, SpeciesList speciesList) {
    // write the data to Elasticsearch
    SpeciesListIndex speciesListIndex =
        new SpeciesListIndex(
            speciesListItem.getId(),
            speciesList.getDataResourceUid(),
            speciesList.getTitle(),
            speciesList.getListType(),
            speciesListItem.getSpeciesListID(),
            speciesListItem.getScientificName(),
            speciesListItem.getVernacularName(),
            speciesListItem.getTaxonID(),
            speciesListItem.getKingdom(),
            speciesListItem.getPhylum(),
            speciesListItem.getClasss(),
            speciesListItem.getOrder(),
            speciesListItem.getFamily(),
            speciesListItem.getGenus(),
            speciesListItem.getProperties().stream()
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue)),
            speciesListItem.getClassification(),
            speciesList.getIsPrivate() != null ? speciesList.getIsPrivate() : false,
            speciesList.getIsAuthoritative() != null ? speciesList.getIsAuthoritative() : false,
            speciesList.getIsBIE() != null ? speciesList.getIsBIE() : false,
            speciesList.getIsSDS() != null ? speciesList.getIsSDS() : false,
            speciesList.getRegion() != null || speciesList.getWkt() != null,
            speciesList.getOwner(),
            speciesList.getEditors(),
            speciesList.getTags(),
            speciesList.getDateCreated() != null ? speciesList.getDateCreated().toString(): null,
            speciesList.getLastUpdated() != null ? speciesList.getLastUpdated().toString(): null,
            speciesList.getLastUpdatedBy()
        );

    speciesListIndexElasticRepository.save(speciesListIndex);
  }

  @SchemaMapping(typeName = "Mutation", field = "addSpeciesListItem")
  public SpeciesListItem addSpeciesListItem(
      @Argument InputSpeciesListItem inputSpeciesListItem,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesList> optionalSpeciesList =
        speciesListMongoRepository.findById(inputSpeciesListItem.getSpeciesListID());
    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    if (!authUtils.isAuthorized(optionalSpeciesList.get(), principal)) {
      throw new AccessDeniedException("You dont have access to this list");
    }

    // add the new entry
    SpeciesListItem speciesListItem = new SpeciesListItem();
    speciesListItem = updateItem(inputSpeciesListItem, speciesListItem, principal);

    // update last updated
    updateLastUpdated(speciesListItem, principal);

    // rematch taxonomy
    try {
      Classification classification = taxonService.lookupTaxon(speciesListItem);
      speciesListItem.setClassification(classification);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }

    // index
    reindex(speciesListItem, optionalSpeciesList.get());
    return speciesListItem;
  }

  private void updateLastUpdated(SpeciesListItem speciesListItem, Principal principal) {
    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListItem.getSpeciesListID());
    if (speciesList.isPresent()) {
      SpeciesList sl = speciesList.get();
      sl.setLastUpdated(new Date());
      sl.setLastUpdatedBy(principal.getName());
      speciesListMongoRepository.save(sl);
    }
  }

  @SchemaMapping(typeName = "Mutation", field = "removeSpeciesListItem")
  public SpeciesListItem removeSpeciesListItem(
      @Argument String id, @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesListItem> optionalSpeciesListItem = speciesListItemMongoRepository.findById(id);
    if (optionalSpeciesListItem.isEmpty()) {
      return null;
    }

    Optional<SpeciesList> optionalSpeciesList =
        speciesListMongoRepository.findById(optionalSpeciesListItem.get().getSpeciesListID());
    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    if (!authUtils.isAuthorized(optionalSpeciesList.get(), principal)) {
      throw new AccessDeniedException("You dont have access to this list");
    }

    // delete the list item
    speciesListItemMongoRepository.deleteById(id);
    speciesListIndexElasticRepository.deleteById(id);

    return optionalSpeciesListItem.get();
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
      @Argument List<String> tags,
      @AuthenticationPrincipal Principal principal) throws Exception {
    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(id);
    if (speciesList.isEmpty()) {
      return null;
    }

    if (authUtils.isAuthorized(speciesList.get(), principal)) {
      boolean reindexRequired = false;

      SpeciesList toUpdate = speciesList.get();

      // check that the supplied list type, region and license is valid
      if (
              !validationService.isValueValid(ConstraintType.lists, listType) ||
              !validationService.isValueValid(ConstraintType.licenses, licence)
      ) {
        throw new Exception("Updated list contains invalid properties for a controlled value (list type, license)");
      }

      if (title != null && !title.equalsIgnoreCase(toUpdate.getTitle())
          || description != null && !description.equalsIgnoreCase(toUpdate.getDescription())
          || listType != null && !listType.equalsIgnoreCase(toUpdate.getListType())
          || isPrivate != null && !isPrivate.equals(toUpdate.getIsPrivate())
          || isAuthoritative != null && !isAuthoritative.equals(toUpdate.getIsAuthoritative())
          || isBIE != null && !isBIE.equals(toUpdate.getIsBIE())
          || isSDS != null && !isSDS.equals(toUpdate.getIsSDS())
          || wkt != null && !wkt.equals(toUpdate.getWkt())
          || region != null && !region.equals(toUpdate.getRegion())
          || licence != null && !licence.equals(toUpdate.getLicence())
          || tags != null && !tags.equals(toUpdate.getTags())) {
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
      toUpdate.setTags(tags);

      // If the visibility has changed, update the visibility of the list items
      // in elasticsearch and mongo
      SpeciesList updatedList = speciesListMongoRepository.save(toUpdate);
      if (reindexRequired) {
        taxonService.reindex(updatedList.getId());
        metadataService.setMeta(updatedList);
      }

      return updatedList;
    } else {
      throw new AccessDeniedException("You dont have access to this list");
    }
  }

  @QueryMapping
  public Page<SpeciesListItem> filterSpeciesList(
      @Argument String speciesListID,
      @Argument String searchQuery,
      @Argument List<Filter> filters,
      @Argument Integer page,
      @Argument Integer size,
      @Argument String sort,
      @Argument String direction,
      @AuthenticationPrincipal Principal principal) {

    if (speciesListID != null) {

      Optional<SpeciesList> speciesListOptional =
          speciesListMongoRepository.findById(speciesListID);
      if (speciesListOptional.isEmpty()) {
        return null;
      }
      if (speciesListOptional.get().getIsPrivate()) {
        // private list, check user is authorized
        if (!authUtils.isAuthorized(speciesListOptional.get(), principal)) {
          logger.info("User not authorized to private access list: " + speciesListID);
          throw new AccessDeniedException("You dont have access to this list");
        }
      }
    }

    Pageable pageableRequest = PageRequest.of(page, size);
    NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
    builder.withQuery(
        q ->
            q.bool(
                bq -> {
                  buildQuery(cleanRawQuery(searchQuery), speciesListID, null, null, filters, bq);
                  return bq;
                }));

    builder.withSort(
        s ->
            s.field(
                new FieldSort.Builder()
                    .field(sort)
                    .order(SortOrder.valueOf(direction != null ? direction : "Asc"))
                    .build()));

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
  public List<Facet> facetSpeciesLists(
      @Argument String searchQuery,
      @Argument List<Filter> filters,
      @Argument String userId,
      @Argument Boolean isPrivate,
      @Argument Integer page,
      @Argument Integer size) {

    // retrieve field list from species_list
    NativeQueryBuilder builder = NativeQuery.builder();
    if (searchQuery != null) {
      builder.withQuery(
          q ->
              q.bool(
                  bq -> {
                    buildQuery(cleanRawQuery(searchQuery), null, userId, isPrivate, filters, bq);
                    return bq;
                  }));
    }

    List<String> facetFields = new ArrayList<>();
    facetFields.add("isAuthoritative");
    facetFields.add("listType");
    facetFields.add("isBIE");
    facetFields.add("isSDS");
    facetFields.add("hasRegion");
    facetFields.add("tags");

    for (String facetField : facetFields) {
      builder.withAggregation(
          facetField, Aggregation.of(a -> a.terms(ta -> ta.field(facetField).size(10))));
    }

    Query aggQuery = builder.build();
    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(aggQuery, SpeciesListIndex.class);

    ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();

    List<Facet> facets = new ArrayList<>();
    facetFields.forEach(
        facetField -> {
          ElasticsearchAggregation agg1 =
              agg.aggregations().stream()
                  .filter(
                      elasticsearchAggregation ->
                          elasticsearchAggregation.aggregation().getName().equals(facetField))
                  .findFirst()
                  .get();

          if (agg1.aggregation().getAggregate().isSterms()) {
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
          } else if (agg1.aggregation().getAggregate().isLterms()) {
            List<LongTermsBucket> array =
                agg1.aggregation().getAggregate().lterms().buckets().array();
            Facet facet = new Facet();
            facet.setCounts(new ArrayList<>());
            facet.setKey(facetField);
            array.forEach(
                bucket -> {
                  facet.getCounts().add(new FacetCount(bucket.keyAsString(), bucket.docCount()));
                });
            facets.add(facet);
          }
        });

    return facets;
  }

  @QueryMapping
  public List<Facet> facetSpeciesList(
      @Argument String speciesListID,
      @Argument String searchQuery,
      @Argument List<Filter> filters,
      @Argument List<String> facetFields,
      @Argument Integer page,
      @Argument Integer size,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListID);
    if (speciesList.isEmpty()) {
      return null;
    }

    // private list, check user is authorized
    if (speciesList.get().getIsPrivate()) {
      if (!authUtils.isAuthorized(speciesList.get(), principal)) {
        logger.info("User not authorized to private access list: " + speciesListID);
        throw new AccessDeniedException("You dont have access to this list");
      }
    }

    // get facet fields unique to this list
    if (facetFields == null || facetFields.isEmpty()) {

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
                a -> a.terms(ta -> ta.field(getPropertiesFacetField(facetField)).size(30))));
      }
    }

    List<String> classificationFields = new ArrayList<>();
    classificationFields.add("classification.family");
    //    classificationFields.add("classification.order");
    //    classificationFields.add("classification.class");
    //    classificationFields.add("classification.phylum");
    classificationFields.add("classification.kingdom");
    classificationFields.add("classification.speciesSubgroup");

    for (String classificationField : classificationFields) {
      builder.withAggregation(
          classificationField,
          Aggregation.of(a -> a.terms(ta -> ta.field(classificationField + ".keyword").size(500))));
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
    //
    // https://bie.ala.org.au/ws/imageSearch/https%3A//id.biodiversity.org.au/taxon/apni/51288314?rows=5&start=0

    Map<String, Object> bieJson = loadJson(String.format(bieTemplateUrl, taxonID));
    if (bieJson != null) {
      String imageID = (String) bieJson.getOrDefault("imageIdentifier", null);
      if (imageID != null) {
        return new Image(String.format(imageTemplateUrl, imageID));
      }
    }
    return null;
  }

  @QueryMapping
  public List<Image> getTaxonImages(
      @Argument String taxonID, @Argument Integer page, @Argument Integer size) throws Exception {
    // get taxon image from BIE
    ObjectMapper objectMapper = new ObjectMapper();
    String url = String.format(bieImagesTemplateUrl, taxonID, size, page * size);
    JsonNode jsonNode = objectMapper.readTree(new URL(url));
    JsonNode results = jsonNode.at("/searchResults/results");
    List<Image> images = new ArrayList<>();
    Iterator<JsonNode> iter = results.elements();
    while (iter.hasNext()) {
      JsonNode node = iter.next();
      images.add(new Image(node.get("largeImageUrl").asText()));
    }
    return images;
  }

  public Map<String, Object> loadJson(String url) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(new URL(url), Map.class);
  }

  private static String getPropertiesFacetField(String filter) {
    if (CORE_FIELDS.contains(filter)) {
      return filter + ".keyword";
    }
    if (filter.startsWith("classification.")) {
      return filter + ".keyword";
    }
    return "properties." + filter + ".keyword";
  }
}
