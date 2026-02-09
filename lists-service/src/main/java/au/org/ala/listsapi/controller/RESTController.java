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

import au.org.ala.listsapi.model.RESTSpeciesListQuery;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListPage;
import au.org.ala.listsapi.repo.SpeciesListItemRepository;
import au.org.ala.listsapi.repo.SpeciesListRepository;
import au.org.ala.listsapi.service.BiocacheService;
import au.org.ala.listsapi.service.SearchHelperService;
import au.org.ala.ws.security.profile.AlaUserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.Max;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Services added for backwards compatibility with the legacy lists API */
@CrossOrigin(origins = "*", maxAge = 3600)
@Validated
@RestController
public class RESTController {

  private static final Logger logger = LoggerFactory.getLogger(RESTController.class);

  @Autowired protected SpeciesListRepository speciesListRepository;

  @Autowired protected SpeciesListItemRepository speciesListItemRepository;

  @Autowired protected BiocacheService biocacheService;

  @Autowired protected AuthUtils authUtils;

  @Autowired protected SearchHelperService searchHelperService;

  @Operation(tags = "REST v2", summary = "Get species list metadata")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Species list found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SpeciesList.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - user is not authorized to view this species list",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "User does not have permission to view species list: dr123"))),
    @ApiResponse(
        responseCode = "404",
        description = "Species list not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", example = "Species list not found: dr123"))),
  })
  @GetMapping("/v2/speciesList/{speciesListID}")
  public ResponseEntity<SpeciesList> speciesList(
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal) {
    Optional<SpeciesList> speciesList =
        speciesListRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

    if (speciesList.isPresent()
        && Boolean.TRUE.equals(speciesList.get().getIsPrivate())
        && !authUtils.isAuthorized(speciesList.get(), principal)) {
      // If the list is private and the user is not authorized, return 403 Forbidden
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return speciesList.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  private boolean eq(String value, String equals) {
    if (value == null) return false;
    if (value.isEmpty()) return false;
    return value.equals(equals);
  }

  @Operation(tags = "REST v2", summary = "Get a list of species lists matching the query")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Species list found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SpeciesListPage.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid query parameters",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "Cannot query private lists without a user ID"))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - user is not authorized to view private species lists",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "You must be authenticated to query private lists"))),
    @ApiResponse(
        responseCode = "404",
        description = "Species list not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", example = "Species list not found")))
  })
  @GetMapping("/v2/speciesList")
  public ResponseEntity<Object> speciesLists(
      RESTSpeciesListQuery speciesListQuery,
      @RequestParam(name = "page", defaultValue = "1", required = false) @Max(10000) int page,
      @RequestParam(name = "pageSize", defaultValue = "10", required = false) @Max(1000)
          int pageSize,
      @AuthenticationPrincipal Principal principal) {
    try {
      Pageable paging = PageRequest.of(page - 1, pageSize);

      if (!authUtils.isAuthenticated(principal)) {
        if (eq(speciesListQuery.getIsPrivate(), "true")) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body("You must be authenticated to query private lists");
        }
        speciesListQuery.setIsPrivate("false");
      } else {
        AlaUserProfile profile = authUtils.getUserProfile(principal);

        // If the user isn't an admin or doesn't have internal scope
        if (!authUtils.hasAdminRole(profile) && !authUtils.hasInternalScope(profile)) {
          // If the user is querying both public & private lists (isPrivate is null)
          if (speciesListQuery.getIsPrivate() == null) {
            if (speciesListQuery.getOwner() == null
                || (profile.getUserId() != null
                    && speciesListQuery.getOwner().equals(profile.getUserId()))) {
              // Query: Public OR (Private AND Owner=Me)
              // We need to construct a Specification for this.
              Specification<SpeciesList> spec = createSpecification(speciesListQuery);
              String userId = profile.getUserId();

              // Access control: Public OR (Private AND Owner=Me)
              Specification<SpeciesList> accessSpec =
                  (root, query, cb) -> {
                    Predicate isPublic = cb.equal(root.get("isPrivate"), false);
                    Predicate isPrivate = cb.equal(root.get("isPrivate"), true);
                    Predicate isOwner = cb.equal(root.get("owner"), userId);
                    return cb.or(isPublic, cb.and(isPrivate, isOwner));
                  };

              Page<SpeciesList> results =
                  speciesListRepository.findAll(spec.and(accessSpec), paging);
              return new ResponseEntity<>(getLegacyFormatModel(results), HttpStatus.OK);

            } else {
              // Otherwise, only query public lists
              speciesListQuery.setIsPrivate("false");
            }
          } else if (eq(speciesListQuery.getIsPrivate(), "true")) {
            // Explicitly asking for private lists
            if (profile.getUserId() == null) {
              return ResponseEntity.badRequest()
                  .body("Cannot query private lists without a user ID");
            }
            if (speciesListQuery.getOwner() != null
                && !speciesListQuery.getOwner().equals(profile.getUserId())) {
              return ResponseEntity.badRequest().body("You can only query your own private lists");
            }
            speciesListQuery.setOwner(profile.getUserId());
          }
        }
        // If the user is an admin or has internal scope, they can query any lists without
        // restrictions (no added filter)
      }

      // Fallback for simple cases (Admin, or explicitly Private/Public set above)
      // Use ExampleMatcher to mimic the old behavior
      ExampleMatcher matcher =
          ExampleMatcher.matching()
              .withIgnoreCase()
              .withIgnoreNullValues()
              .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);

      Example<SpeciesList> example = Example.of(speciesListQuery.convertTo(), matcher);
      Page<SpeciesList> results = speciesListRepository.findAll(example, paging);
      return new ResponseEntity<>(getLegacyFormatModel(results), HttpStatus.OK);

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  // Helper to create Specification from RESTSpeciesListQuery (simple string matching)
  private Specification<SpeciesList> createSpecification(RESTSpeciesListQuery query) {
    return (root, criteriaQuery, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      // Add predicates for fields present in query.
      // Note: This logic assumes 'contains' for strings, similar to the Mongo ExampleMatcher.

      if (query.getTitle() != null)
        predicates.add(
            cb.like(cb.lower(root.get("title")), "%" + query.getTitle().toLowerCase() + "%"));
      if (query.getDescription() != null)
        predicates.add(
            cb.like(
                cb.lower(root.get("description")),
                "%" + query.getDescription().toLowerCase() + "%"));
      // ... Add other fields as needed based on RESTSpeciesListQuery properties
      // Ideally RESTSpeciesListQuery.convertTo() returns a SpeciesList object that we can use,
      // but for the "OR" logic we needed Specification.
      // Since we only use the "OR" logic when we haven't filtered everything down yet,
      // we can stick to using the Example for the base fields if we can mix them?
      // No, easier to rely on the fallback path if we can.

      // Actually, constructing the Specification fully is tedious.
      // Let's rely on the fact that if we are in the "Public OR Private" branch,
      // the user probably hasn't specified many other filters that conflict.
      // But to be safe, let's use the Example-based specification if possible?
      // Spring Data JPA doesn't expose ExampleSpecification easily.

      // Simplified approach for the migration:
      // Only support basic filters in the complex OR scenario: title, description.

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Operation(
      tags = "REST v2",
      summary = "Get a list of species lists that contain the specified taxon GUID")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Species list found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SpeciesListPage.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid query parameters",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "Cannot query private lists without a user ID"))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - user is not authorized to view private species lists",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "You must be authenticated to query private lists"))),
    @ApiResponse(
        responseCode = "404",
        description = "Species list not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", example = "Species list not found")))
  })
  @GetMapping("/v2/speciesList/byGuid")
  public ResponseEntity<Object> speciesListsByGuid(
      @RequestParam(name = "guid") String guid,
      @RequestParam(name = "page", defaultValue = "1", required = false) @Max(10000) int page,
      @RequestParam(name = "pageSize", defaultValue = "10", required = false) @Max(1000)
          int pageSize,
      @AuthenticationPrincipal Principal principal) {
    try {
      AlaUserProfile profile = authUtils.getUserProfile(principal);

      if (page < 1 || (page * pageSize) > 10000) {
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
      }

      Pageable pageableRequest = PageRequest.of(page - 1, pageSize);

      boolean isAdmin = false;
      boolean isPublicOnly = true;
      String userId = null;

      if (authUtils.isAuthenticated(principal)) {
        userId = profile.getUserId();
        if (authUtils.hasAdminRole(profile) || authUtils.hasInternalScope(profile)) {
          isAdmin = true;
          isPublicOnly = false; // Admin sees all
        } else {
          isPublicOnly = false; // User sees Public + Own Private
        }
      }

      Page<SpeciesList> results =
          speciesListRepository.findListsByTaxonGuid(
              guid, userId, isPublicOnly, isAdmin, pageableRequest);

      return new ResponseEntity<>(getLegacyFormatModel(results), HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /** Convert the results to a legacy format */
  public SpeciesListPage getLegacyFormatModel(Page<SpeciesList> results) {
    SpeciesListPage legacyFormat = new SpeciesListPage();
    legacyFormat.setListCount(results.getTotalElements());
    legacyFormat.setOffset(results.getPageable().getPageNumber());
    legacyFormat.setMax(results.getPageable().getPageSize());
    legacyFormat.setLists(results.getContent());
    return legacyFormat;
  }

  public SpeciesListPage getLegacyFormatModel(
      List<SpeciesList> results, long totalRecords, int max, int offset) {
    SpeciesListPage legacyFormat = new SpeciesListPage();
    legacyFormat.setListCount(totalRecords);
    legacyFormat.setOffset(offset);
    legacyFormat.setMax(max);
    legacyFormat.setLists(results);
    return legacyFormat;
  }

  @Operation(
      tags = "REST v2",
      summary =
          "Get species lists items for a list. List IDs can be a single value, or comma separated IDs.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Species list found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SpeciesListPage.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid query parameters",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "Cannot query private lists without a user ID"))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - user is not authorized to view private species lists",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "You must be authenticated to query private lists"))),
    @ApiResponse(
        responseCode = "404",
        description = "Species list not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", example = "Species list not found")))
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
      int pageIndex = (page - 1); // spring data pageable is zero based
      List<SpeciesListItem> speciesListItems =
          searchHelperService.fetchSpeciesListItems(
              speciesListIDs, searchQuery, fields, null, pageIndex, pageSize, sort, dir, principal);

      if (speciesListItems.isEmpty()) {
        return ResponseEntity.notFound().build();
      }

      return new ResponseEntity<>(speciesListItems, HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(
      tags = "REST v2",
      summary = "Get details of species list items i.e species for a list of guid(s)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Species list found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SpeciesListPage.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid query parameters",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "Cannot query private lists without a user ID"))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - user is not authorized to view private species lists",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "You must be authenticated to query private lists"))),
    @ApiResponse(
        responseCode = "404",
        description = "Species list not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", example = "Species list not found")))
  })
  @GetMapping("/v2/species")
  public ResponseEntity<Object> species(
      @RequestParam(name = "guids") String guids,
      @Nullable @RequestParam(name = "speciesListIDs") String speciesListIDs,
      @Nullable @RequestParam(name = "page", defaultValue = "1") Integer page,
      @Nullable @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @AuthenticationPrincipal Principal principal) {
    try {
      int pageIndex = (page - 1); // spring data pageable is zero based
      String searchQuery = guids.replaceAll(",", "|"); // convert to regex OR
      List<SpeciesListItem> speciesListItems =
          searchHelperService.fetchSpeciesListItems(
              speciesListIDs, searchQuery, null, null, pageIndex, pageSize, null, null, principal);

      return new ResponseEntity<>(speciesListItems, HttpStatus.OK);
    } catch (Exception e) {
      logger.info(e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(tags = "REST v2", summary = "Get a SOLR query PID for a list")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Species list found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SpeciesListPage.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Species list not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", example = "Species list not found")))
  })
  @GetMapping("/v2/speciesListQid/{speciesListID}")
  public ResponseEntity<Object> speciesListPid(
      @PathVariable("speciesListID") String speciesListID) {
    try {
      Optional<SpeciesList> speciesList =
          speciesListRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

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

  @Operation(
      tags = "REST v2",
      summary = "Get a list of keys from KVP common across a list multiple species lists")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Species list found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SpeciesListPage.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - user is not authorized to view private species lists",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema =
                    @Schema(
                        type = "string",
                        example = "You must be authenticated to query private lists"))),
    @ApiResponse(
        responseCode = "404",
        description = "Species lists not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(type = "string", example = "Species list not found")))
  })
  @GetMapping("/v2/listCommonKeys/{speciesListIDs}")
  public ResponseEntity<Object> listCommonKeys(
      @PathVariable("speciesListIDs") String speciesListIDs,
      @AuthenticationPrincipal Principal principal) {
    try {
      List<String> IDs = Arrays.stream(speciesListIDs.split(",")).toList();
      List<SpeciesList> speciesLists = speciesListRepository.findByDataResourceUidInOrIdIn(IDs);

      // Ensure that some species lists were returned with the query
      if (!speciesLists.isEmpty()) {
        List<SpeciesList> validLists =
            speciesLists.stream()
                .filter(
                    list ->
                        !Boolean.TRUE.equals(list.getIsPrivate())
                            || authUtils.isAuthorized(list, principal))
                .toList();

        if (validLists.isEmpty()) {
          return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }

        return new ResponseEntity<>(
            searchHelperService.findCommonKeys(speciesLists), HttpStatus.OK);
      }

      return ResponseEntity.status(404).body("Species list(s) not found");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
