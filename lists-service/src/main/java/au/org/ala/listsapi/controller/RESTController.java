package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.SpeciesListCustomRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.BiocacheService;
import au.org.ala.ws.security.profile.AlaUserProfile;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.security.Principal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Services added for backwards compatibility with the legacy lists API */
@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class RESTController {

  private static final Logger logger = LoggerFactory.getLogger(RESTController.class);

  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;

  @Autowired protected SpeciesListCustomRepository speciesListCustomRepository;

  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

  @Autowired protected BiocacheService biocacheService;

  @Autowired protected AuthUtils authUtils;

  @Autowired protected ElasticsearchOperations elasticsearchOperations;
  @Operation(tags = "REST", summary = "Get species list metadata")
  @Tag(name = "REST", description = "REST Services for species lists lookups")
  @GetMapping("/speciesList/{speciesListID}")
  public ResponseEntity<SpeciesList> speciesList(
      @PathVariable("speciesListID") String speciesListID) {
    Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
    return speciesList.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  private boolean eq(String value, String equals) {
    if (value == null) return false;
    if (value.isEmpty()) return false;
    return value.equals(equals);
  }

  @Operation(tags = "REST", summary = "Get a list of species lists matching the query")
  @GetMapping("/speciesList/")
  public ResponseEntity<Object> speciesLists(
      RESTSpeciesListQuery speciesList,
      @RequestParam(name = "page", defaultValue = "1", required = false) int page,
      @RequestParam(name = "pageSize", defaultValue = "10", required = false) int pageSize,
      @AuthenticationPrincipal Principal principal) {
    try {
      Pageable paging = PageRequest.of(page - 1, pageSize);

      if (!authUtils.isAuthenticated(principal)) {
        logger.info(Boolean.toString(authUtils.isAuthenticated(principal)));
        if (eq(speciesList.getIsPrivate(), "true")) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("You must be authenticated to query private lists");
        }
      } else {
        AlaUserProfile profile = authUtils.getUserProfile(principal);

        // If the user isn't an admin
        if (!authUtils.hasAdminRole(profile)) {
          // If the user is querying both public & private lists
          if (speciesList.getIsPrivate() == null) {
            // If no owner is supplied, or the owner is the current user,
            // query all the user's private lists, and all public lists
            if (speciesList.getOwner() == null || speciesList.getOwner().equals(profile.getUserId())) {
              RESTSpeciesListQuery privateLists = speciesList.copy();
              privateLists.setIsPrivate("true");
              privateLists.setOwner(profile.getUserId());

              RESTSpeciesListQuery publicLists = speciesList.copy();
              publicLists.setIsPrivate("false");

              Page<SpeciesList> results = speciesListCustomRepository.findByMultipleExamples(privateLists.convertTo(), publicLists.convertTo(), paging);
              return new ResponseEntity<>(getLegacyFormat(results), HttpStatus.OK);
            } else { // Otherwise, only query public lists with that userid
              speciesList.setIsPrivate("false");
            }
          } else if (eq(speciesList.getIsPrivate(), "true")) {
            if (speciesList.getOwner() != null && !speciesList.getOwner().equals(profile.getUserId())) {
              return ResponseEntity.badRequest().body("You can only query your own private lists");
            }
            speciesList.setOwner(profile.getUserId());
          }
        }
      }

      if (speciesList == null || speciesList.isEmpty()) {
        Page<SpeciesList> results = speciesListMongoRepository.findAll(paging);
        return new ResponseEntity<>(getLegacyFormat(results), HttpStatus.OK);
      }

      ExampleMatcher matcher =
          ExampleMatcher.matching()
              .withIgnoreCase()
              .withIgnoreNullValues()
              .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);

      // Create an Example from the exampleProduct with the matcher
      Example<SpeciesList> example = Example.of(speciesList.convertTo(), matcher);
      Page<SpeciesList> results = speciesListMongoRepository.findAll(example, paging);
      return new ResponseEntity<>(getLegacyFormat(results), HttpStatus.OK);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  public Map<String, Object> getLegacyFormat(Page<SpeciesList> results) {
    Map<String, Object> legacyFormat = new HashMap<>();
    legacyFormat.put("listCount", results.getTotalElements());
    legacyFormat.put("offset", results.getPageable().getPageNumber());
    legacyFormat.put("max", results.getPageable().getPageSize());
    legacyFormat.put("lists", results.getContent());
    return legacyFormat;
  }

  private String emptyDefault(String value, String defaultValue) {
    return value.isEmpty() ? defaultValue : value;
  }

  @Operation(tags = "REST", summary = "Get species lists items for a list. List IDs can be a single value, or comma separated IDs.")
  @GetMapping("/speciesListItems/{speciesListIDs}")
  public ResponseEntity<Object> speciesListItemsElastic(
          @PathVariable("speciesListIDs") String speciesListIDs,
          @Nullable @RequestParam(name = "q") String searchQuery,
          @Nullable @RequestParam(name = "page", defaultValue = "1") Integer page,
          @Nullable @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
          @Nullable @RequestParam(name = "sort", defaultValue="scientificName") String sort,
          @Nullable @RequestParam(name = "dir", defaultValue="asc") String dir,
          @AuthenticationPrincipal Principal principal) {
    try {
      List<String> IDs = Arrays.stream(speciesListIDs.split(",")).toList();
      List<SpeciesList> foundLists = speciesListMongoRepository.findAllByDataResourceUidIsInOrIdIsIn(IDs, IDs);

      // Ensure that some species lists were returned with the query
      if (!foundLists.isEmpty()) {
        List<FieldValue> validIDs = foundLists.stream()
                .filter(list -> !list.getIsPrivate() || authUtils.isAuthorized(list, principal))
                .map(list -> FieldValue.of(list.getId())).toList();

        if (page < 1 || (page * pageSize) > 10000 || validIDs.isEmpty()) {
          return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }

        ArrayList<Filter> tempFilters = new ArrayList<>();
        Pageable pageableRequest = PageRequest.of(page - 1, pageSize);
        NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
        builder.withQuery(
                q ->
                        q.bool(
                                bq -> {
                                  ElasticUtils.buildQuery(ElasticUtils.cleanRawQuery(searchQuery), validIDs, null, null, tempFilters, bq);
                                  return bq;
                                }));

        builder.withSort(
                s ->
                        s.field(
                                new FieldSort.Builder()
                                        .field(emptyDefault(sort, "scientificName"))
                                        .order(emptyDefault(dir, "asc").equals("asc") ? SortOrder.Asc : SortOrder.Desc)
                                        .build()));

        Query query = builder.build();
        query.setPageable(pageableRequest);
        SearchHits<SpeciesListIndex> results =
                elasticsearchOperations.search(
                        query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

        List<SpeciesListItem> speciesListItems =
                ElasticUtils.convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));

        return new ResponseEntity<>(speciesListItems, HttpStatus.OK);
      }

      return ResponseEntity.status(404).body("Species list not found");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(tags = "REST", summary = "Get details of species list items i.e species for a list of guid(s)")
  @GetMapping("/species/")
  public ResponseEntity<Object> species(
          @RequestParam(name = "guids") String guids,
          @Nullable @RequestParam(name = "speciesListIDs") String speciesListIDs,
          @Nullable @RequestParam(name = "page", defaultValue = "1") Integer page,
          @Nullable @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
          @Nullable @RequestParam(name = "sort", defaultValue="scientificName") String sort,
          @Nullable @RequestParam(name = "dir", defaultValue="asc") String dir,
          @AuthenticationPrincipal Principal principal) {
    try {
      AlaUserProfile profile = authUtils.getUserProfile(principal);
      List<FieldValue> GUIDs = Arrays.stream(guids.split(",")).map(FieldValue::of).toList();
      List<FieldValue> listIDs = speciesListIDs != null ?
              Arrays.stream(speciesListIDs.split(",")).map(FieldValue::of).toList() : null;

      if (page < 1 || (page * pageSize) > 10000) {
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
      }

      Pageable pageableRequest = PageRequest.of(page - 1, pageSize);
      NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
      builder.withQuery(
              q ->
                      q.bool(
                              bq -> {
                                bq.filter(f -> f.terms(t -> t.field("classification.taxonConceptID")
                                        .terms(ta -> ta.value(GUIDs))));

                                if (listIDs != null) {
                                  bq.filter(f -> f.bool(b -> b
                                          .should(s -> s.terms(t -> t.field("speciesListID").terms(ta -> ta.value(listIDs))))
                                          .should(s -> s.terms(t -> t.field("dataResourceUid").terms(ta -> ta.value(listIDs))))
                                  ));
                                }

                                // If the user is not an admin, only query their private lists, and all other public lists
                                if (!authUtils.isAuthenticated(principal)) {
                                  bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
                                } else if (!authUtils.hasAdminRole(profile)) {
                                  bq.filter(f -> f.bool(b -> b
                                          .should(s -> s.bool(b2 -> b2
                                                  .must(m -> m.term(t -> t.field("owner").value(profile.getUserId())))
                                                  .must(m -> m.term(t -> t.field("isPrivate").value(true)))
                                          ))
                                          .should(s -> s.term(t -> t.field("isPrivate").value(false)))
                                  ));
                                }

                                return bq;
                              }));

      builder.withSort(
              s ->
                      s.field(
                              new FieldSort.Builder()
                                      .field(emptyDefault(sort, "scientificName"))
                                      .order(emptyDefault(dir, "asc").equals("asc") ? SortOrder.Asc : SortOrder.Desc)
                                      .build()));

      Query query = builder.build();
      query.setPageable(pageableRequest);
      SearchHits<SpeciesListIndex> results =
              elasticsearchOperations.search(
                      query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

      List<SpeciesListItem> speciesListItems =
              ElasticUtils.convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));

      return new ResponseEntity<>(speciesListItems, HttpStatus.OK);

    } catch (Exception e) {
      logger.info(e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(tags = "REST", summary = "Get a SOLR query PID for a list")
  @GetMapping("/speciesListQid/{speciesListID}")
  public ResponseEntity<Object> speciesListPid(
          @PathVariable("speciesListID") String speciesListID) {
    try {
      Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

      // Ensure the species list exists
      if (speciesList.isPresent()) {
        String qid = biocacheService.getQidForSpeciesList(speciesList.get().getId());
        return new ResponseEntity<>(Collections.singletonMap("qid", qid), HttpStatus.OK);
      }

      return ResponseEntity.status(404).body("Species list not found");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
