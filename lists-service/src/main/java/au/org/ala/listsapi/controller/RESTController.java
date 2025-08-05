/*
 * Copyright (C) 2025 Atlas of Living Australia
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

package au.org.ala.listsapi.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import au.org.ala.listsapi.model.RESTSpeciesListQuery;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListPage;
import au.org.ala.listsapi.repo.SpeciesListCustomRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.BiocacheService;
import au.org.ala.listsapi.service.SearchHelperService;
import au.org.ala.listsapi.util.ElasticUtils;
import au.org.ala.ws.security.profile.AlaUserProfile;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/** Services added for backwards compatibility with the legacy lists API */
@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class RESTController {

    private static final Logger logger = LoggerFactory.getLogger(RESTController.class);

    @Autowired
    protected SpeciesListMongoRepository speciesListMongoRepository;

    @Autowired
    protected SpeciesListCustomRepository speciesListCustomRepository;

    @Autowired
    protected BiocacheService biocacheService;

    @Autowired
    protected AuthUtils authUtils;

    @Autowired
    protected SearchHelperService searchHelperService;

    @Autowired
    protected ElasticsearchOperations elasticsearchOperations;

    @Operation(tags = "REST v2", summary = "Get species list metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesList.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "string", example = "User does not have permission to view species list: dr123"))),
            @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "string", example = "Species list not found: dr123"))),
    })
    @GetMapping("/v2/speciesList/{speciesListID}")
    public ResponseEntity<SpeciesList> speciesList(
            @PathVariable("speciesListID") String speciesListID,
            @AuthenticationPrincipal Principal principal) {
        Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID,
                speciesListID);

        if (speciesList.isPresent() && speciesList.get().getIsPrivate() && !authUtils.isAuthorized(speciesList.get(), principal)) {
            // If the list is private and the user is not authorized, return 403 Forbidden
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return speciesList.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    private boolean eq(String value, String equals) {
        if (value == null)
            return false;
        if (value.isEmpty())
            return false;
        return value.equals(equals);
    }

    @Operation(tags = "REST v2", summary = "Get a list of species lists matching the query")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesListPage.class)))
    })
    @GetMapping("/v2/speciesList")
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
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("You must be authenticated to query private lists");
                }
            } else {
                AlaUserProfile profile = authUtils.getUserProfile(principal);

                // If the user isn't an admin or doesn't have internal scope
                if (!authUtils.hasAdminRole(profile) && !authUtils.hasInternalScope(profile)) {
                    // If the user is querying both public & private lists
                    if (speciesList.getIsPrivate() == null) {
                        // If no owner is supplied, or the owner is the current user,
                        // query all the user's private lists, and all public lists
                        if (speciesList.getOwner() == null || (profile.getUserId() != null && speciesList.getOwner().equals(profile.getUserId()))) {
                            RESTSpeciesListQuery privateLists = speciesList.copy();
                            privateLists.setIsPrivate("true");
                            privateLists.setOwner(profile.getUserId());

                            RESTSpeciesListQuery publicLists = speciesList.copy();
                            publicLists.setIsPrivate("false");

                            Page<SpeciesList> results = speciesListCustomRepository
                                    .findByMultipleExamples(privateLists.convertTo(), publicLists.convertTo(), paging);
                            return new ResponseEntity<>(getLegacyFormatModel(results), HttpStatus.OK);
                        } else { // Otherwise, only query public lists with that userid
                            speciesList.setIsPrivate("false");
                        }
                    } else if (eq(speciesList.getIsPrivate(), "true")) {
                        if (profile.getUserId() == null) {
                            return ResponseEntity.badRequest().body("Cannot query private lists without a user ID");
                        }
                        if (speciesList.getOwner() != null && !speciesList.getOwner().equals(profile.getUserId())) {
                            return ResponseEntity.badRequest().body("You can only query your own private lists");
                        }
                        speciesList.setOwner(profile.getUserId());
                    }
                }
                // If the user is an admin or has internal scope, they can query any lists without restrictions
            }

            if (speciesList == null || speciesList.isEmpty()) {
                Page<SpeciesList> results = speciesListMongoRepository.findAll(paging);
                return new ResponseEntity<>(getLegacyFormatModel(results), HttpStatus.OK);
            }

            ExampleMatcher matcher = ExampleMatcher.matching()
                    .withIgnoreCase()
                    .withIgnoreNullValues()
                    .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);

            // Create an Example from the exampleProduct with the matcher
            Example<SpeciesList> example = Example.of(speciesList.convertTo(), matcher);
            Page<SpeciesList> results = speciesListMongoRepository.findAll(example, paging);
            return new ResponseEntity<>(getLegacyFormatModel(results), HttpStatus.OK);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(tags = "REST v2", summary = "Get a list of species lists that contain the specified taxon GUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesListPage.class)))
    })
    @GetMapping("/v2/speciesList/byGuid")
    public ResponseEntity<Object> speciesListsByGuid(
            @RequestParam(name = "guid") String guid,
            @RequestParam(name = "page", defaultValue = "1", required = false) int page,
            @RequestParam(name = "pageSize", defaultValue = "10", required = false) int pageSize,
            @AuthenticationPrincipal Principal principal) {
        try {
            AlaUserProfile profile = authUtils.getUserProfile(principal);

            if (page < 1 || (page * pageSize) > 10000) {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
            }

            Pageable pageableRequest = PageRequest.of(page - 1, pageSize);
            NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
            builder.withQuery(
                    q -> q.bool(
                            bq -> {
                                // Require at least one of these conditions to match using minimumShouldMatch
                                bq.minimumShouldMatch("1");

                                // classification.taxonConceptID.keyword == guid
                                bq.should(s -> s.term(t -> t
                                        .field("classification.taxonConceptID.keyword")
                                        .value(guid)));

                                // taxonID.keyword == guid
                                bq.should(s -> s.term(t -> t
                                        .field("taxonID.keyword")
                                        .value(guid)));

                                // If the user is not an admin or doesn't have internal scope, only query their private lists, and all other
                                // public lists
                                if (!authUtils.isAuthenticated(principal)) {
                                    bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
                                } else if (!authUtils.hasAdminRole(profile) && !authUtils.hasInternalScope(profile)) {
                                    if (profile.getUserId() != null) {
                                        bq.filter(f -> f.bool(b -> b
                                                .should(s -> s.bool(b2 -> b2
                                                        .must(m -> m.term(t -> t.field("owner").value(profile.getUserId())))
                                                        .must(m -> m.term(t -> t.field("isPrivate").value(true)))))
                                                .should(s -> s.term(t -> t.field("isPrivate").value(false)))));
                                    } else {
                                        // If user has no userId (e.g., M2M token without internal scope), only show public lists
                                        bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
                                    }
                                }
                                // If user is admin or has internal scope, no additional filters are applied (can see all lists)

                                return bq;
                            }))
                    .withFieldCollapse(FieldCollapse.of(fc -> fc.field("speciesListID.keyword")));

            Query query = builder.build();
            query.setPageable(pageableRequest);

            SearchHits<SpeciesListIndex> results = elasticsearchOperations.search(
                    query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

            List<SpeciesListItem> speciesListItems = ElasticUtils
                    .convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));

            // If no results matching the guid were found, return an empty list
            if (results.getTotalHits() == 0) {
                return new ResponseEntity<>(getLegacyFormatModel(new ArrayList<>(), 0, page, pageSize), HttpStatus.OK);
            }

            List<SpeciesList> speciesLists = speciesListMongoRepository
                    .findAllById(speciesListItems.stream().map(SpeciesListItem::getSpeciesListID).toList());

            return new ResponseEntity<>(getLegacyFormatModel(speciesLists, results.getTotalHits(), page, pageSize),
                    HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Convert the results to a legacy format
     * Note: this is not 100% backwards compatible with the old API, see the
     * 
     * @au.org.ala.listsapi.LegacyController
     *
     * @param results
     * @return
     */
    public SpeciesListPage getLegacyFormatModel(Page<SpeciesList> results) {
        SpeciesListPage legacyFormat = new SpeciesListPage();
        legacyFormat.setListCount(results.getTotalElements());
        legacyFormat.setOffset(results.getPageable().getPageNumber());
        legacyFormat.setMax(results.getPageable().getPageSize());
        legacyFormat.setLists(results.getContent());
        return legacyFormat;
    }

    public SpeciesListPage getLegacyFormatModel(List<SpeciesList> results, long totalRecords, int max, int offset) {
        SpeciesListPage legacyFormat = new SpeciesListPage();
        legacyFormat.setListCount(totalRecords);
        legacyFormat.setOffset(offset);
        legacyFormat.setMax(max);
        legacyFormat.setLists(results);
        return legacyFormat;
    }

    @Operation(tags = "REST v2", summary = "Get species lists items for a list. List IDs can be a single value, or comma separated IDs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list item found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesListItem.class)))
    })
    @GetMapping("/v2/speciesListItems/{speciesListIDs}")
    public ResponseEntity<Object> speciesListItems(
            @PathVariable("speciesListIDs") String speciesListIDs,
            @Nullable @RequestParam(name = "q") String searchQuery,
            @Nullable @RequestParam(name = "fields") String fields,
            @Nullable @RequestParam(name = "page", defaultValue = "1") Integer page,
            @Nullable @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @Nullable @RequestParam(name = "sort", defaultValue = "scientificName") String sort,
            @Nullable @RequestParam(name = "dir", defaultValue = "asc") String dir,
            @AuthenticationPrincipal Principal principal) {
        try {
            List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(speciesListIDs,
                    searchQuery, fields, page, pageSize, sort, dir, principal);

            if (speciesListItems.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return new ResponseEntity<>(speciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(tags = "REST v2", summary = "Get details of species list items i.e species for a list of guid(s)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list items found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesListItem.class)))
    })
    @GetMapping("/v2/species")
    public ResponseEntity<Object> species(
            @RequestParam(name = "guids") String guids,
            @Nullable @RequestParam(name = "speciesListIDs") String speciesListIDs,
            @Nullable @RequestParam(name = "page", defaultValue = "1") Integer page,
            @Nullable @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @AuthenticationPrincipal Principal principal) {
        try {
            List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(guids, speciesListIDs,
                    page, pageSize, principal);
            return new ResponseEntity<>(speciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            logger.info(e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(tags = "REST v2", summary = "Get a SOLR query PID for a list")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesList.class)))
    })
    @GetMapping("/v2/speciesListQid/{speciesListID}")
    public ResponseEntity<Object> speciesListPid(
            @PathVariable("speciesListID") String speciesListID) {
        try {
            Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID,
                    speciesListID);

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

    @Operation(tags = "REST v2", summary = "Get a list of keys from KVP common across a list multiple species lists")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesList.class)))
    })
    @GetMapping("/v2/listCommonKeys/{speciesListIDs}")
    public ResponseEntity<Object> listCommonKeys(
            @PathVariable("speciesListIDs") String speciesListIDs,
            @AuthenticationPrincipal Principal principal) {
        try {
            List<String> IDs = Arrays.stream(speciesListIDs.split(",")).toList();
            List<SpeciesList> speciesLists = speciesListMongoRepository.findAllByDataResourceUidIsInOrIdIsIn(IDs, IDs);

            // Ensure that some species lists were returned with the query
            if (!speciesLists.isEmpty()) {
                List<SpeciesList> validLists = speciesLists.stream()
                        .filter(list -> !list.getIsPrivate() || authUtils.isAuthorized(list, principal)).toList();

                if (validLists.isEmpty()) {
                    return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
                }

                return new ResponseEntity<>(searchHelperService.findCommonKeys(speciesLists), HttpStatus.OK);
            }

            return ResponseEntity.status(404).body("Species list(s) not found");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
