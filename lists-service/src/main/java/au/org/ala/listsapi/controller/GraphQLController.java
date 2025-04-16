package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.ReleaseMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.MetadataService;
import au.org.ala.listsapi.service.TaxonService;
import au.org.ala.listsapi.service.ValidationService;
import au.org.ala.ws.security.profile.AlaUserProfile;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.net.URL;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphQL API for lists
 */
@Controller
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "GraphQL", description = "GraphQL Services for species lists lookups")
public class GraphQLController {

  private static final Logger logger = LoggerFactory.getLogger(GraphQLController.class);

  @Value("${elastic.maximumDocuments}")
  public static final int MAX_LIST_ENTRIES = 10000;

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

  public static final String SPECIES_LIST_ID = "speciesListID";
  @Autowired
  protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired
  protected ReleaseMongoRepository releaseMongoRepository;
  @Autowired
  protected ElasticsearchOperations elasticsearchOperations;
  @Autowired
  protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
  @Autowired
  protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
  @Autowired
  protected MongoUtils mongoUtils;

  @Autowired
  protected TaxonService taxonService;
  @Autowired
  protected ValidationService validationService;
  @Autowired
  protected AuthUtils authUtils;
  @Autowired
  protected MetadataService metadataService;

  static Date parsedDate(String date) {
    try {
      return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(date);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Search across lists
   *
   * @param searchQuery
   * @param page
   * @param size
   * @param userId
   * @param isPrivate
   * @param sort
   * @param dir
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
      @Argument String sort,
      @Argument String dir,
      @AuthenticationPrincipal Principal principal) {

    // if searching private lists, check user is authorized
    if (isPrivate) {
      AlaUserProfile profile = authUtils.getUserProfile(principal);
      if (profile == null) {
        logger.info("User not authorized to private access lists");
        throw new AccessDeniedException("You must be logged in to view private lists");
      } else {
        userId = profile.getUserId();
      }
    }

    NativeQueryBuilder builder = NativeQuery.builder().withPageable(PageRequest.of(1, 1));
    String finalUserId = userId;
    builder.withQuery(
        q ->
            q.bool(
                bq -> ElasticUtils.buildQuery(ElasticUtils.cleanRawQuery(searchQuery), (String) null,
                   finalUserId, isPrivate, filters, bq)));

    builder.withAggregation(
        "types_count",
        Aggregation.of(a -> a.cardinality(ca -> ca.field(SPECIES_LIST_ID + ".keyword"))));

    // aggregation on species list ID
    builder.withAggregation(
        SPECIES_LIST_ID,
        Aggregation.of(a -> a.terms(ta -> ta.field(SPECIES_LIST_ID + ".keyword").size(MAX_LIST_ENTRIES))));

    Query aggQuery = builder.build();

    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(aggQuery, SpeciesListIndex.class);

    ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();

    assert agg != null;
    ElasticsearchAggregation cardinality = agg.aggregations().get(0);
    long noOfLists = cardinality.aggregation().getAggregate().cardinality().value();

    ElasticsearchAggregation agg1 = agg.aggregations().get(1);
    List<StringTermsBucket> array = agg1.aggregation().getAggregate().sterms().buckets().array();

    Map<String, Long> speciesListIDs =
        array.stream()
            .collect(
                Collectors.toMap(
                    bucket -> bucket.key().stringValue(), MultiBucketBase::docCount));

    // lookup species list metadata
    Iterable<SpeciesList> speciesLists =
        speciesListMongoRepository.findAllById(speciesListIDs.keySet());

    // Add row count
    for (SpeciesList speciesList : speciesLists) {
      speciesList.setRowCount(speciesListIDs.get(speciesList.getId()).intValue());
    }

    List<SpeciesList> result = new ArrayList<>();
    speciesLists.forEach(result::add);

    // Sort the list using the provided sort and dir parameters
    if (StringUtils.isNotEmpty(sort) && StringUtils.isNotEmpty(dir)) {
      result.sort(getSpeciesListComparator(sort, dir));
    } else {
      // Default sort to title if no params are passed in.
      result.sort(getSpeciesListComparator("title", "asc"));
    }

    // Apply pagination after sorting.
    List<SpeciesList> paginatedResult = result.stream()
        .skip((long) page * size)
        .limit(size)
        .collect(Collectors.toList());

    return new PageImpl<>(paginatedResult, PageRequest.of(page, size), noOfLists);
  }


  /**
   * Creates a comparator for sorting `SpeciesList` objects based on the given sort field and direction.
   *
   * @param sort The field to sort by.
   * @param dir  The direction of the sort ("asc" or "desc").
   * @return A `Comparator<SpeciesList>` that can be used to sort a list of `SpeciesList` objects.
   */
  private Comparator<SpeciesList> getSpeciesListComparator(String sort, String dir) {
    Comparator<SpeciesList> comparator;
    boolean ascending = "asc".equalsIgnoreCase(dir);

    switch (sort) {
      case "title":
        comparator = Comparator.comparing(SpeciesList::getTitle, String.CASE_INSENSITIVE_ORDER);
        break;
      case "listType":
        // listType contains null values, so we need to handle nulls separately so they always appear at the end
        // noting that order is reversed when descending
        comparator = ascending
            ?
            Comparator.comparing(SpeciesList::getListType, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            :
            Comparator.comparing(SpeciesList::getListType,
                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
        break;
      case "rowCount":
        comparator = Comparator.comparing(SpeciesList::getRowCount, Integer::compareTo);
        break;
      case "lastUpdated":
      default:
        comparator = Comparator.comparing(SpeciesList::getLastUpdated, Date::compareTo);
        break;
    }

    // Apply null handling based on direction
    if (!ascending) {
      comparator = comparator.reversed();
    }

    return comparator;
  }

  @GraphQlExceptionHandler
  public GraphQLError handle(@NonNull Throwable ex, @NonNull DataFetchingEnvironment environment) {
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
    Optional<SpeciesList> speciesListOptional =
        speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
    if (speciesListOptional.isPresent()) {
      SpeciesList speciesList = speciesListOptional.get();

      // private list, check user is authorized
      if (speciesList.getIsPrivate()
          && !authUtils.isAuthorized(speciesList, principal)) {
        logger.info("User not authorized to private access list: " + speciesListID);
        throw new AccessDeniedException("You don't have access to this list");
      }

      return speciesList;
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

    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    SpeciesList toUpdate = optionalSpeciesList.get();

    if (!authUtils.isAuthorized(toUpdate, principal)) {
      logger.info("User not authorized to modify access list: " + id);
      throw new AccessDeniedException("You dont have authorisation to modify this list");
    }

    toUpdate.getFieldList().add(fieldName);

    if (StringUtils.isNotEmpty(fieldValue)) {
      int batchSize = MAX_LIST_ENTRIES;
      ObjectId lastId = null;

      boolean finished = false;
      while (!finished) {
        List<SpeciesListItem> items =
            speciesListItemMongoRepository.findNextBatch(toUpdate.getId(), lastId,
                PageRequest.of(0, batchSize));

        for (SpeciesListItem item : items) {
          item.getProperties().add(new KeyValue(fieldName, fieldValue));
        }

        if (!items.isEmpty()) {
          mongoUtils.speciesListItemsBulkUpdate(items, List.of("properties"));
        }

        if (items.size() < batchSize) {
          finished = true;
        } else {
          lastId = items.get(items.size() - 1).getId();
        }
      }
    }

    return speciesListMongoRepository.save(toUpdate);
  }

  @SchemaMapping(typeName = "Mutation", field = "renameField")
  public SpeciesList renameField(
      @Argument String id,
      @Argument String oldName,
      @Argument String newName,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    SpeciesList toUpdate = optionalSpeciesList.get();

    if (!authUtils.isAuthorized(toUpdate, principal)) {
      logger.info("User not authorized to modify access list: " + id);
      throw new AccessDeniedException("You dont have access to this list");
    }

    // remove from species list metadata
    toUpdate.getFieldList().remove(oldName);
    toUpdate.getFieldList().add(newName);

    int batchSize = MAX_LIST_ENTRIES;
    ObjectId lastId = null;

    boolean finished = false;
    while (!finished) {
      List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(toUpdate.getId(), lastId,
          PageRequest.of(0, batchSize));

      for (SpeciesListItem item : items) {
        Optional<KeyValue> kv =
            item.getProperties().stream().filter(k -> k.getKey().equals(oldName)).findFirst();
        kv.ifPresent(keyValue -> keyValue.setKey(newName));
      }

      if (!items.isEmpty()) {
        mongoUtils.speciesListItemsBulkUpdate(items, List.of("properties"));
      }

      if (items.size() < batchSize) {
        finished = true;
      } else {
        lastId = items.get(items.size() - 1).getId();
      }
    }
    return speciesListMongoRepository.save(toUpdate);
  }

  @SchemaMapping(typeName = "Mutation", field = "removeField")
  public SpeciesList removeField(
      @Argument String id,
      @Argument String fieldName,
      @AuthenticationPrincipal Principal principal) {

    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    SpeciesList toUpdate = optionalSpeciesList.get();

    if (!authUtils.isAuthorized(toUpdate, principal)) {
      logger.info("User not authorized to modify access list: " + id);
      throw new AccessDeniedException("You dont have access to this list");
    }

    toUpdate.getFieldList().remove(fieldName);

    int batchSize = MAX_LIST_ENTRIES;
    ObjectId lastId = null;

    boolean finished = false;
    while (!finished) {
      List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(toUpdate.getId(), lastId,
          PageRequest.of(0, batchSize));

      for (SpeciesListItem item : items) {
        Optional<KeyValue> kv =
            item.getProperties().stream().filter(k -> k.getKey().equals(fieldName)).findFirst();
        kv.ifPresent(keyValue -> item.getProperties().remove(keyValue));
      }

      if (!items.isEmpty()) {
        mongoUtils.speciesListItemsBulkUpdate(items, List.of("properties"));
      }

      if (items.size() < batchSize) {
        finished = true;
      } else {
        lastId = items.get(items.size() - 1).getId();
      }
    }

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
    speciesList = updateLastUpdated(speciesList, principal);

    // rematch taxonomy
    try {
      Classification classification = taxonService.lookupTaxon(speciesListItem);
      speciesListItem.setClassification(classification);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }

    // reindex the item
    reindex(speciesListItem, speciesList);

    // update distinct match count
    speciesList.setDistinctMatchCount(taxonService.getDistinctTaxaCount(speciesList.getId()));
    speciesListMongoRepository.save(speciesList);

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
            speciesListItem.getId().toString(),
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
            speciesListItem.getProperties(),
            speciesListItem.getClassification(),
            speciesList.getIsPrivate() != null ? speciesList.getIsPrivate() : false,
            speciesList.getIsAuthoritative() != null ? speciesList.getIsAuthoritative() : false,
            speciesList.getIsBIE() != null ? speciesList.getIsBIE() : false,
            speciesList.getIsSDS() != null ? speciesList.getIsSDS() : false,
            speciesList.getRegion() != null || speciesList.getWkt() != null,
            speciesList.getOwner(),
            speciesList.getEditors(),
            speciesList.getTags(),
            speciesList.getDateCreated() != null ? speciesList.getDateCreated().toString() : null,
            speciesList.getLastUpdated() != null ? speciesList.getLastUpdated().toString() : null,
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

    SpeciesList speciesList = optionalSpeciesList.get();

    if (!authUtils.isAuthorized(speciesList, principal)) {
      throw new AccessDeniedException("You dont have access to this list");
    }

    // add the new entry
    SpeciesListItem speciesListItem = new SpeciesListItem();
    speciesListItem = updateItem(inputSpeciesListItem, speciesListItem, principal);

    // update last updated
    speciesList = updateLastUpdated(speciesList, principal);

    // rematch taxonomy
    try {
      Classification classification = taxonService.lookupTaxon(speciesListItem);
      speciesListItem.setClassification(classification);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }

    // index
    reindex(speciesListItem, optionalSpeciesList.get());

    // update distinct match count
    speciesList.setDistinctMatchCount(taxonService.getDistinctTaxaCount(speciesList.getId()));
    speciesList.setRowCount(speciesList.getRowCount() + 1);
    speciesListMongoRepository.save(speciesList);

    return speciesListItem;
  }

  private SpeciesList updateLastUpdated(SpeciesList speciesList, Principal principal) {
    speciesList.setLastUpdated(new Date());
    speciesList.setLastUpdatedBy(principal.getName());
    return speciesListMongoRepository.save(speciesList);
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

    SpeciesList speciesList = optionalSpeciesList.get();

    if (!authUtils.isAuthorized(optionalSpeciesList.get(), principal)) {
      throw new AccessDeniedException("You dont have access to this list");
    }

    // delete the list item
    speciesListItemMongoRepository.deleteById(id);
    speciesListIndexElasticRepository.deleteById(id);

    // update distinct match count
    speciesList.setDistinctMatchCount(taxonService.getDistinctTaxaCount(speciesList.getId()));
    speciesList.setRowCount(speciesList.getRowCount() - 1);
    speciesListMongoRepository.save(speciesList);

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
    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    SpeciesList toUpdate = optionalSpeciesList.get();

    if (authUtils.isAuthorized(toUpdate, principal)) {
      boolean reindexRequired = false;

      // check that the supplied list type, region and license is valid
      if (
          !validationService.isValueValid(ConstraintType.listType, listType) ||
              !validationService.isValueValid(ConstraintType.licence, licence)
      ) {
        throw new Exception(
            "Updated list contains invalid properties for a controlled value (list type, license)");
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

      if (!toUpdate.getIsPrivate() || toUpdate.getIsAuthoritative()) {
        metadataService.setMeta(toUpdate);
      }

      // If the visibility has changed, update the visibility of the list items
      // in elasticsearch and mongo
      SpeciesList updatedList = speciesListMongoRepository.save(toUpdate);
      if (reindexRequired) {
        taxonService.reindex(updatedList.getId());
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
      @Argument String dir,
      @AuthenticationPrincipal Principal principal) {

    String ID;

    if (speciesListID != null) {

      Optional<SpeciesList> speciesListOptional =
          speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

      if (speciesListOptional.isEmpty()) {
        return null;
      }

      SpeciesList speciesList = speciesListOptional.get();

      if (speciesList.getIsPrivate()) {
        // private list, check user is authorized
        if (!authUtils.isAuthorized(speciesList, principal)) {
          logger.info("User not authorized to private access list: " + speciesListID);
          throw new AccessDeniedException("You dont have access to this list");
        }
      }

      ID = speciesList.getId();
    } else {
      ID = null;
    }

    Pageable pageableRequest = PageRequest.of(page, size);
    NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
    builder.withQuery(
        q ->
            q.bool(
                bq -> {
                  ElasticUtils.buildQuery(ElasticUtils.cleanRawQuery(searchQuery), ID, null, null,
                      filters, bq);
                  return bq;
                }));

    builder.withSort(
        s ->
            s.field(
                new FieldSort.Builder()
                    .field(sort)
                    .order(dir.equals("asc") ? SortOrder.Asc : SortOrder.Desc)
                    .build()));

    Query query = builder.build();
    query.setPageable(pageableRequest);
    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(
            query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

    List<SpeciesListItem> speciesLists =
        ElasticUtils.convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));

    return new PageImpl<>(speciesLists, pageableRequest, results.getTotalHits());
  }

  @NotNull
  private static String cleanRawQuery(String searchQuery) {
    if (searchQuery != null)
      return searchQuery.trim().replace("\"", "\\\"");
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
                    ElasticUtils.buildQuery(ElasticUtils.cleanRawQuery(searchQuery), (String) null,
                        userId, isPrivate, filters, bq);
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

    // Define the name for the nested cardinality aggregation
    String distinctCountAggName = "distinct_species_list_count";
    // Ensure you use the .keyword field for exact matching and cardinality
    String speciesListIdKeywordField = SPECIES_LIST_ID + ".keyword";

    // Define which of the facet fields are boolean
    Set<String> booleanFacetFields = Set.of("isAuthoritative", "isBIE", "isSDS", "hasRegion");

    for (String facetField : facetFields) {
      // Determine the correct field for the terms aggregation
      String esTermsField;
      if (booleanFacetFields.contains(facetField)) {
        // Use the base field name for boolean types
        esTermsField = facetField;
      } else if (facetField.equals("tags")) {
        // Tags is often multi-value keyword, use .keyword if mapped that way
        esTermsField = facetField + ".keyword"; // Assuming tags is keyword
      } else {
        // Use the helper for others (assuming it correctly adds .keyword where needed)
        // Or explicitly handle listType.keyword etc.
        esTermsField = getPropertiesFacetField(facetField); // Make sure this handles listType correctly
      }

      builder.withAggregation(
          facetField, // Name of the outer terms aggregation
          Aggregation.of(a -> a // 'a' is the Aggregation.Builder
              .terms(ta -> ta // Configure the terms aggregation
                  .field(esTermsField) // Use the correctly determined field name
                  .size(50) // Set your desired size
              )
              .aggregations( // Add sub-aggregations to the parent builder 'a'
                  distinctCountAggName, // Name for the nested aggregation
                  sa -> sa // 'sa' is the Aggregation.Builder for the sub-aggregation
                      .cardinality(ca -> ca // Configure the cardinality aggregation
                              .field(speciesListIdKeywordField)
                          // Cardinality still on speciesListID
                      )
              )
          )
      );
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
                  // Get the nested cardinality aggregation result by its name
                  Aggregate distinctCountAggResult = bucket.aggregations().get(distinctCountAggName);

                  // Extract the distinct count value
                  long distinctListCount = distinctCountAggResult.cardinality().value();

                  // Create the FacetCount with the distinct count
                  facet.getCounts().add(
                      new FacetCount(
                          bucket.key().stringValue(),
                          // Or bucket.keyAsString() for LongTermsBucket
                          distinctListCount // Use the distinct count here
                      )
                  );
                });
            facets.add(facet);
          } else if (agg1.aggregation().getAggregate().isLterms()) {
            List<LongTermsBucket> array =
                agg1.aggregation().getAggregate().lterms().buckets().array();
            Facet facet = new Facet();
            facet.setCounts(new ArrayList<>());
            facet.setKey(facetField); // Keep the original facet field name (e.g., "isAuthoritative")
            array.forEach(
                bucket -> {
                  // Get the nested cardinality aggregation result by its name
                  Aggregate distinctCountAggResult = bucket.aggregations().get(distinctCountAggName);

                  // Extract the distinct count value
                  long distinctListCount = distinctCountAggResult.cardinality().value();

                  // Convert the long key (0 or 1) to a boolean string ("false" or "true")
                  long keyAsLong = bucket.key();
                  String keyAsString = (keyAsLong == 1) ? "true" : "false";

                  // Create the FacetCount with the distinct count and the "true"/"false" key
                  facet.getCounts().add(
                      new FacetCount(
                          keyAsString, // Use the converted "true" or "false" string
                          distinctListCount // Use the distinct count here
                      )
                  );
                });
            // Add the facet only if it has counts (optional but good practice)
            if (!facet.getCounts().isEmpty()) {
              facets.add(facet);
            }
          }
        }); // End of facetFields.forEach

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

    Optional<SpeciesList> optionalSpeciesList =
        speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
    if (optionalSpeciesList.isEmpty()) {
      return null;
    }

    SpeciesList speciesList = optionalSpeciesList.get();

    // private list, check user is authorized
    if (speciesList.getIsPrivate()) {
      if (!authUtils.isAuthorized(speciesList, principal)) {
        logger.info("User not authorized to private access list: " + speciesListID);
        throw new AccessDeniedException("You dont have access to this list");
      }
    }

    // get facet fields unique to this list
    if (facetFields == null || facetFields.isEmpty()) {
      facetFields = speciesList.getFacetList();
    }

    // Create the NativeQueryBuilder
    NativeQueryBuilder builder = NativeQuery.builder();

    // Build a query that only filters by speciesList ID and searchQuery
    // (not applying the additional filters for aggregations)
    builder.withQuery(
        q ->
            q.bool(
                bq -> {
                  // Build a query that only includes speciesListID and searchQuery constraints
                  // but NOT the additional filters - this is used for aggregations
                  if (speciesList.getId() != null) {
                    bq.must(m -> m.term(t -> t.field(SPECIES_LIST_ID).value(speciesList.getId())));
                  }

                  if (StringUtils.isNotEmpty(searchQuery)) {
                    bq.must(m -> m.queryString(qs ->
                        qs.query(ElasticUtils.cleanRawQuery(searchQuery))
                    ));
                  }

                  return bq;
                }));

    // Now add a post filter that includes ALL constraints
    // This will filter the results but not affect the aggregations
    if (filters != null && !filters.isEmpty()) {
      builder.withFilter(
          q ->
              q.bool(
                  bq -> {
                    ElasticUtils.buildQuery(ElasticUtils.cleanRawQuery(searchQuery),
                        speciesList.getId(), null, null, filters, bq);
                    return bq;
                  }));
    }

    // Add aggregations for the facet fields
    if (facetFields != null) {
      for (String facetField : facetFields) {
        builder.withAggregation(
            facetField,
            Aggregation.of(
                a -> a.terms(ta -> ta.field(getPropertiesFacetField(facetField)).size(30))));
      }
    }

    // Add classification fields
    List<String> classificationFields = new ArrayList<>();
    classificationFields.add("classification.family");
    classificationFields.add("classification.order");
    classificationFields.add("classification.class");
    classificationFields.add("classification.phylum");
    classificationFields.add("classification.kingdom");
    classificationFields.add("classification.speciesSubgroup");

    for (String classificationField : classificationFields) {
      builder.withAggregation(
          classificationField,
          Aggregation.of(a -> a.terms(ta -> ta.field(classificationField + ".keyword").size(500))));
    }

    // Add properties.key aggregation for property facets
    builder.withAggregation(
        "properties_keys",
        Aggregation.of(a -> a.nested(na -> na.path("properties"))
            .aggregations("key_counts", sa -> sa.terms(ta -> ta.field("properties.key.keyword").size(100))))
    );

    Query aggQuery = builder.build();
    SearchHits<SpeciesListIndex> results =
        elasticsearchOperations.search(aggQuery, SpeciesListIndex.class);

    ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();

    // Process aggregations for facets
    List<Facet> facets = new ArrayList<>();
    if (facetFields != null) {
      facetFields.addAll(classificationFields);
      facetFields.forEach(
          facetField -> {
            ElasticsearchAggregation agg1 =
                agg.aggregations().stream()
                    .filter(
                        elasticsearchAggregation ->
                            elasticsearchAggregation.aggregation().getName()
                                .equals(facetField))
                    .findFirst()
                    .orElse(null);

            if (agg1 != null && agg1.aggregation().getAggregate().isSterms()) {
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
            }
          });
    }

    // First identify all keys from the properties_keys aggregation
    List<String> propertyKeys = new ArrayList<>();
    ElasticsearchAggregation propertiesAgg = agg.aggregations().stream()
        .filter(elasticsearchAggregation ->
            elasticsearchAggregation.aggregation().getName().equals("properties_keys"))
        .findFirst()
        .orElse(null);

    if (propertiesAgg != null) {
      Aggregate nestedAgg = propertiesAgg.aggregation().getAggregate();
      if (nestedAgg.isNested()) {
        Aggregate keyCountsAgg = nestedAgg.nested().aggregations().get("key_counts");
        if (keyCountsAgg != null && keyCountsAgg.isSterms()) {
          List<StringTermsBucket> keyBuckets = keyCountsAgg.sterms().buckets().array();
          keyBuckets.forEach(bucket -> {
            propertyKeys.add(bucket.key().stringValue());
          });
        }
      }
    }

    // For each property key, create a new facet with key.value pairs
    for (String propertyKey : propertyKeys) {
      // Create an aggregation for this specific key
      NativeQueryBuilder keyValueBuilder = NativeQuery.builder();

      // Copy the main query constraints
      keyValueBuilder.withQuery(
          q -> q.bool(bq -> {
            if (speciesList.getId() != null) {
              bq.must(m -> m.term(t -> t.field(SPECIES_LIST_ID).value(speciesList.getId())));
            }
            if (StringUtils.isNotEmpty(searchQuery)) {
              bq.must(m -> m.queryString(qs -> qs.query(ElasticUtils.cleanRawQuery(searchQuery))));
            }
            return bq;
          })
      );

      // Add nested aggregation for this specific property key's values
      keyValueBuilder.withAggregation(
          propertyKey + "_values",
          Aggregation.of(a -> a.nested(na -> na.path("properties"))
              .aggregations("filtered_values", sa -> sa.filter(f -> f.term(t -> t.field("properties.key.keyword").value(propertyKey)))
                  .aggregations("value_counts", va -> va.terms(ta -> ta.field("properties.value.keyword").size(100)))
              )
          )
      );

      // Execute the query for this key
      Query keyValueQuery = keyValueBuilder.build();
      SearchHits<SpeciesListIndex> keyValueResults = elasticsearchOperations.search(keyValueQuery, SpeciesListIndex.class);

      ElasticsearchAggregations keyValueAggs = (ElasticsearchAggregations) keyValueResults.getAggregations();
      if (keyValueAggs != null) {
        ElasticsearchAggregation keyValueAgg = keyValueAggs.aggregations().stream()
            .filter(a -> a.aggregation().getName().equals(propertyKey + "_values"))
            .findFirst()
            .orElse(null);

        if (keyValueAgg != null) {
          Aggregate nestedKeyValueAgg = keyValueAgg.aggregation().getAggregate();
          if (nestedKeyValueAgg.isNested()) {
            Aggregate filteredValuesAgg = nestedKeyValueAgg.nested().aggregations().get("filtered_values");
            if (filteredValuesAgg != null && filteredValuesAgg.isFilter()) {
              Aggregate valueCountsAgg = filteredValuesAgg.filter().aggregations().get("value_counts");
              if (valueCountsAgg != null && valueCountsAgg.isSterms()) {
                List<StringTermsBucket> valueBuckets = valueCountsAgg.sterms().buckets().array();
                if (!valueBuckets.isEmpty()) {
                  Facet propertyValueFacet = new Facet();
                  propertyValueFacet.setKey("properties." + propertyKey);
                  propertyValueFacet.setCounts(new ArrayList<>());

                  valueBuckets.forEach(bucket -> {
                    propertyValueFacet.getCounts().add(
                        new FacetCount(
                            bucket.key().stringValue(),
                            bucket.docCount()
                        )
                    );
                  });

                  facets.add(propertyValueFacet);
                }
              }
            }
          }
        }
      }
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
