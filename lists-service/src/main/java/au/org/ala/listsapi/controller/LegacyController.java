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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

import com.fasterxml.jackson.annotation.JsonView;

import au.org.ala.listsapi.config.Views;
import au.org.ala.listsapi.model.ErrorResponse;
import au.org.ala.listsapi.model.QueryListItemVersion1;
import au.org.ala.listsapi.model.RESTSpeciesListQuery;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesItemVersion1;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListItemVersion1;
import au.org.ala.listsapi.model.SpeciesListPageVersion1;
import au.org.ala.listsapi.model.SpeciesListVersion1;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.SearchHelperService;
import au.org.ala.listsapi.service.SpeciesListLegacyService;
import au.org.ala.ws.security.profile.AlaUserProfile;
import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;

/**
 * Controller for legacy API endpoints (/v1/**), that are deprecated but still supported for backwards compatibility.
 * @author dos009@csiro.au
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@Validated
@RestController
public class LegacyController {

    private static final Logger logger = LoggerFactory.getLogger(LegacyController.class);

    @Autowired
    protected SpeciesListMongoRepository speciesListMongoRepository;

    @Autowired
    protected SpeciesListLegacyService legacyService;

    @Autowired
    protected SearchHelperService searchHelperService;

    @Autowired
    protected AuthUtils authUtils;

    @SecurityRequirement(name = "JWT")
    @Operation(tags = "REST v1", summary = "Get species list metadata for all lists", deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species lists found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesListPageVersion1.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid parameters", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
    })
    @GetMapping({"/v1/speciesList", "/v1/speciesList/"})
    public ResponseEntity<Object> speciesListSearch(
            @Nullable @RequestParam(name = "isAuthoritative") String isAuthoritative,
            @Nullable @RequestParam(name = "isThreatened") String isThreatened,
            @Nullable @RequestParam(name = "isInvasive") String isInvasive,
            @Nullable @RequestParam(name = "isSDS") String isSDS,
            @Nullable @RequestParam(name = "isBIE") String isBIE,
            @Parameter(description = "The data resource id (or speciesListID)", example = "dr656")  
            @Nullable @RequestParam(name = "druid") String druid,
            @Parameter(description = "Query string (q)")
            @Nullable @RequestParam(name = "q") String query,
            @Parameter(description = "Sort field")
            @Schema(allowableValues = {"count", "listName", "listType", "dateCreated", "lastUpdated", "ownerFullName", "region", "category", "authority"})
            @RequestParam(name = "sort", defaultValue = "listName", required = false) String sort,
            @Parameter(description = "Sort direction")
            @Schema(allowableValues = {"asc", "desc"})
            @RequestParam(name = "order", defaultValue = "asc") String order,
            @RequestParam(name = "max", defaultValue = "25", required = false) @Max(10000) int max,
            @RequestParam(name = "offset", defaultValue = "0", required = false) @Max(9990) int offset,
            @AuthenticationPrincipal Principal principal) {
        try {
            int page = offset / max;
            Pageable paging = PageRequest.of(page, max);
            RESTSpeciesListQuery speciesListQuery = new RESTSpeciesListQuery();
            fixLegacyBooleanSyntax(isAuthoritative, isThreatened, isInvasive, isSDS, isBIE, druid, speciesListQuery);

            SpeciesList convertedSpeciesListQuery = speciesListQuery.convertTo();
            AlaUserProfile profile = authUtils.getUserProfile(principal);
            String userId = profile != null ? profile.getUserId() : null;
            Boolean isAdmin = authUtils.hasAdminRole(profile);
            query = StringUtils.isNotBlank(query) ? URLDecoder.decode(query, StandardCharsets.UTF_8) : ".*"; // regex for all if blank
            Page<SpeciesList> results = searchHelperService.searchDocuments(convertedSpeciesListQuery, userId, isAdmin, query, paging);
            SpeciesListPageVersion1 legacyFormat = getLegacyFormatModel(results);
            legacyFormat.setSort(sort);
            legacyFormat.setOrder(order);
            return new ResponseEntity<>(legacyFormat, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error occurred for /v1/speciesList: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Fix the sort field to map from legacty API to the new field names.
     * 
     * @param sort the sort field provided by the user
     * @return the fixed sort field
     */
    private String fixSortField(String sort) {
        // Map user-provided sort fields to database field names
        Map<String, String> sortFieldMapping = Map.of(
            "count", "rowCount",
            "listName", "title",
            "ownerFullName", "ownerName",
            "guid", "classification.taxonConceptID",
            "region", "hasRegion",
            "rawScientificName", "suppliedName"
        );

        return sortFieldMapping.getOrDefault(sort, sort); // Default to the provided field if no mapping exists
    }

    /**
     * Fix legacy boolean syntax from the old API (eq:true, eq:false) to just true/false.
     * and add to the speciesListQuery object.
     * 
     * @param isAuthoritative
     * @param isThreatened
     * @param isInvasive
     * @param speciesListQuery
     */
    private static void fixLegacyBooleanSyntax(String isAuthoritative, String isThreatened, String isInvasive,
            String isSDS, String isBIE, String druid, RESTSpeciesListQuery speciesListQuery) {
        if (StringUtils.isNotBlank(isAuthoritative)) {
            speciesListQuery.setIsAuthoritative(isAuthoritative.replaceAll("eq:", "")); // eq:true to true, etc.
        }            
        
        if (StringUtils.isNotBlank(isThreatened)) {
            speciesListQuery.setIsThreatened(isThreatened.replaceAll("eq:", "")); // eq:true to true, etc.
        }          
        
        if (StringUtils.isNotBlank(isInvasive)) {
            speciesListQuery.setIsInvasive(isInvasive.replaceAll("eq:", "")); // eq:true to true, etc.
        }

        if (StringUtils.isNotBlank(isSDS)) {
            speciesListQuery.setIsSDS(isSDS.replaceAll("eq:", "")); // eq:true to true, etc.
        }

        if (StringUtils.isNotBlank(isBIE)) {
            speciesListQuery.setIsBIE(isBIE.replaceAll("eq:", "")); // eq:true to true, etc.
        }

        if (StringUtils.isNotBlank(druid)) {
            speciesListQuery.setDataResourceUid(druid); 
        }
    }

    /**
     * Convert the results to a version 1 legacy format
     * Note: this is not 100% backwards compatible with the old API, see the
     * 
     * @au.org.ala.listsapi.LegacyController
     *
     * @param results
     * @return
     */
    public SpeciesListPageVersion1 getLegacyFormatModel(Page<SpeciesList> results) {
        SpeciesListPageVersion1 legacyFormat = new SpeciesListPageVersion1();
        legacyFormat.setListCount(results.getTotalElements());
        legacyFormat.setOffset(results.getPageable().getPageNumber());
        legacyFormat.setMax(results.getPageable().getPageSize());
        legacyFormat.setLists(legacyService.convertListToVersion1(results.getContent()));
        return legacyFormat;
    }

    /*
     * Internal method to handle both the search-based and direct ID lookup for species list items, 
     * as the logic is mostly shared between the two endpoints. The presence of list filter parameters 
     * or absence of druid indicates a search-based request, otherwise it's a direct ID lookup.
     */
    private ResponseEntity<Object> internalSpeciesListItems(
            String druid, String isAuthoritative, String isThreatened, String isInvasive,
            String isSDS, String isBIE, String query, Boolean nonulls, String sort, 
            String order, String dir, Integer max, Integer offset, Principal principal) {
        try {
            if (Boolean.TRUE.equals(nonulls)) {
                return ResponseEntity.badRequest().body("The 'nonulls' parameter is not yet supported.");
            }

            String effectiveOrder = StringUtils.isNotBlank(order) ? order : (StringUtils.isNotBlank(dir) ? dir : "asc");
            // Logic preserved: 25 if searching, 10 if specific ID
            int effectiveMax = (max != null) ? max : (StringUtils.isBlank(druid) ? 25 : 10);
            int effectiveOffset = (offset != null) ? offset : 0;

            if (effectiveOffset != 0 && (effectiveOffset % effectiveMax) != 0) {
                return ResponseEntity.badRequest().body(
                        String.format("Invalid pagination: 'offset' (%d) must be a multiple of 'max' (%d).",
                                effectiveOffset, effectiveMax));
            }

            boolean hasListFilters = StringUtils.isNotBlank(isAuthoritative) ||
                    StringUtils.isNotBlank(isThreatened) || StringUtils.isNotBlank(isInvasive) ||
                    StringUtils.isNotBlank(isSDS) || StringUtils.isNotBlank(isBIE);

            String decodedQuery = StringUtils.isNotBlank(query) ? URLDecoder.decode(query, StandardCharsets.UTF_8) : "";

            // Branch A: List Search Required
            if (hasListFilters || StringUtils.isBlank(druid)) {
                Integer page = effectiveOffset / effectiveMax;
                Pageable paging = PageRequest.of(0, 10000);
                RESTSpeciesListQuery speciesListQuery = new RESTSpeciesListQuery();
                fixLegacyBooleanSyntax(isAuthoritative, isThreatened, isInvasive, isSDS, isBIE, druid, speciesListQuery);

                SpeciesList convertedSpeciesListQuery = speciesListQuery.convertTo();
                AlaUserProfile profile = authUtils.getUserProfile(principal);
                String userId = profile != null ? profile.getUserId() : null;
                Boolean isAdmin = authUtils.hasAdminRole(profile);

                Page<SpeciesList> speciesLists = searchHelperService.searchDocuments(convertedSpeciesListQuery, userId, isAdmin, decodedQuery, paging);

                if (speciesLists.isEmpty()) {
                    return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
                }

                String speciesListIDs = speciesLists.getContent().stream()
                        .map(list -> list.getDataResourceUid() != null ? list.getDataResourceUid() : list.getId())
                        .collect(java.util.stream.Collectors.joining(","));

                String itemSort = sort != null ? fixSortField(sort) : "speciesListID";

                List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(
                        speciesListIDs,
                        StringUtils.isNotBlank(decodedQuery) ? decodedQuery : null,
                        null, null, page, effectiveMax, itemSort, effectiveOrder, principal);

                if (speciesListItems.isEmpty()) {
                    return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
                }

                return new ResponseEntity<>(legacyService.convertListItemToVersion1(speciesListItems), HttpStatus.OK);
            } 
            // Branch B: Direct ID Lookup
            else {
                return getLegacySpeciesListItems(druid, decodedQuery, null, nonulls, effectiveOffset, effectiveMax, sort, effectiveOrder, principal);
            }
        } catch (Exception e) {
            logger.error("Error occurred for species list items: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Return species list items via query or guid lookup.
     */
    @SecurityRequirement(name = "JWT")
    @Operation(tags = "REST v1", summary = "Search species list items", deprecated = true,
            description = "Search across items using filters. Use 'druid' as a query parameter here.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species lists items found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = List.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid parameters", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
    })
    @GetMapping("/v1/speciesListItems")
    public ResponseEntity<Object> searchSpeciesListItems(
            @Parameter(description = "Filter by list ID (druid) as query parameter", example = "dr656")
            @RequestParam(name = "druid", required = false) String druid,
            @RequestParam(name = "isAuthoritative", required = false) String isAuthoritative,
            @RequestParam(name = "isThreatened", required = false) String isThreatened,
            @RequestParam(name = "isInvasive", required = false) String isInvasive,
            @RequestParam(name = "isSDS", required = false) String isSDS,
            @RequestParam(name = "isBIE", required = false) String isBIE,
            @Parameter(description = "Query string to find items within the specified list", example = "Eucalyptus" )
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "nonulls", required = false) Boolean nonulls,
            @RequestParam(name = "sort", defaultValue = "speciesListID") String sort,
            @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "dir", required = false) String dir,
            @RequestParam(name = "max", required = false) Integer max,
            @RequestParam(name = "offset", defaultValue = "0") Integer offset,
            @AuthenticationPrincipal Principal principal) {
        
        return internalSpeciesListItems(druid, isAuthoritative, isThreatened, isInvasive, isSDS, isBIE, 
                                    query, nonulls, sort, order, dir, max, offset, principal);
    }

    /**
     * Return species list items for a specific list ID, with optional query and nonulls filter.
     */
    @SecurityRequirement(name = "JWT")
    @Operation(tags = "REST v1", summary = "Get items by specific List ID", deprecated = true,
            description = "Retrieve items for a specific list ID provided in the URL path.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species lists items found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = List.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid parameters", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
    })
    @JsonView(Views.Narrow.class)
    @GetMapping({"/v1/speciesListItems/{druid}", "/v1/speciesListItems/{druid}/"})
    public ResponseEntity<Object> getSpeciesListItemsByPath(
            @Parameter(description = "The species list ID path parameter", example = "dr656") 
            @PathVariable(name = "druid") String druid,
            @Parameter(description = "Query string to find items within the specified list", example = "Eucalyptus" )
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "sort", defaultValue = "speciesListID") String sort,
            @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "max", required = false) Integer max,
            @RequestParam(name = "offset", defaultValue = "0") Integer offset,
            @AuthenticationPrincipal Principal principal) {
        
        // Note: Filter booleans are nullified here as path lookup is specific to one list
        return internalSpeciesListItems(druid, null, null, null, null, null, 
                                    query, null, sort, order, null, max, offset, principal);
    }

    @SecurityRequirement(name = "JWT")
    @Operation(tags = "REST v1", summary = "Get species list metadata for a given species list ID", deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesListVersion1.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
    })
    @GetMapping({"/v1/speciesList/{speciesListID}", "/v1/speciesList/{speciesListID}/"})
    public ResponseEntity<Object> speciesList(
            @Parameter(description = "The species list ID or data resource ID", example = "dr656")
            @PathVariable("speciesListID") String speciesListID,
            @AuthenticationPrincipal Principal principal) {
        return getListDetails(speciesListID, principal);
    }

    @SecurityRequirement(name = "JWT")
    @Operation(tags = "REST v1", summary = "(Internal use) Get  species list metadata for a given species list ID", deprecated = true, hidden = false)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species list found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SpeciesListVersion1.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
    })
    @GetMapping({"/v1/speciesListInternal/{speciesListID}", "/v1/speciesListInternal/{speciesListID}/"})
    public ResponseEntity<Object> speciesListInternal(
            @Parameter(description = "The species list ID or data resource ID", example = "dr656")
            @PathVariable("speciesListID") String speciesListID, 
            @AuthenticationPrincipal Principal principal) {
        
        // Check if the user/client app is authorized to access this endpoint
        if (!authUtils.isAuthorized(principal)) {
            ErrorResponse errorResponse = new ErrorResponse(HttpStatus.FORBIDDEN.name(), "Not authorised to access this endpoint", HttpStatus.FORBIDDEN.value());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse);
        }

        return getListDetails(speciesListID, principal);
    }

    /**
     * Get the details of a species list by its ID or data resource UID.
     * This method is used by both the public and internal endpoints.
     * 
     * @param speciesListID
     * @return
     */
    private ResponseEntity<Object> getListDetails(String speciesListID, 
                                                    @AuthenticationPrincipal Principal principal) {

        Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

        if (speciesList.isPresent() && speciesList.get().isPrivate() && !authUtils.isAuthorized(speciesList.get(), principal)) {
            ErrorResponse errorResponse = new ErrorResponse(HttpStatus.FORBIDDEN.name(), "Not authorised to access this endpoint", HttpStatus.FORBIDDEN.value());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse);
        }

        return speciesList
            .map(list -> ResponseEntity.<Object>ok(legacyService.convertListToVersion1(list)))
            .orElseGet(() -> {
                ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.name(),
                "Species list not found for id: " + speciesListID,
                HttpStatus.NOT_FOUND.value()
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse);
            });
    }



    @Operation(tags = "REST v1", summary = "Get species list items (internal use) for a given species list ID", deprecated = true)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Species list found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpeciesListItemVersion1.class)
                            
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid parameters", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),

    })
    @JsonView(Views.Narrow.class)
    @SecurityRequirement(name = "JWT")
    @GetMapping({"/v1/speciesListItemsInternal/{druid}", "/v1/speciesListItemsInternal/{druid}/"})
    public ResponseEntity<Object> speciesListInternal(
            @Parameter(
                    name = "druid",
                    description = "The data resource id (or speciesListID) or comma separated ids to identify list(s) to return list items for e.g. '/v1/speciesListItemsInternal/dr123,dr456,dr789'",
                    example = "dr18404,dr18457",
                    schema = @Schema(type = "string")
            )
            @PathVariable("druid") String speciesListIDs,
            @Parameter(
                    name = "includeKVP",
                    description = "Whether to include KVP (key value pairs) values in the returned list item. Note this is now ignored and  KVP values are always returned.",
                    schema = @Schema(type = "boolean", defaultValue = "true")
            )
            @RequestParam(name = "includeKVP", defaultValue = "false") Boolean _includeKVP,
            @Nullable @RequestParam(name = "q") String searchQuery,
            @Nullable @RequestParam(name = "nonulls") Boolean nonulls,
            @Nullable @RequestParam(name = "offset", defaultValue = "0") Integer offset,
            @Nullable @RequestParam(name = "max", defaultValue = "10") Integer max,
            @Nullable @RequestParam(name = "sort", defaultValue="speciesListID") String sort,
            @Nullable @RequestParam(name = "dir", defaultValue="asc") String dir,
            @AuthenticationPrincipal Principal principal) {
        try {
            if (Boolean.TRUE.equals(nonulls)) {
                // TODO: remove this code when nonulls is supported
                return ResponseEntity.badRequest().body("The 'nonulls' parameter is not yet supported.");
            }
            // Check if the user/client app is authorized to access this endpoint
            if (!authUtils.isAuthorized(principal)) {
                ErrorResponse errorResponse = new ErrorResponse(HttpStatus.FORBIDDEN.name(), "Not authorised to access this endpoint", HttpStatus.FORBIDDEN.value());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorResponse);
            }

            return getLegacySpeciesListItems(speciesListIDs, searchQuery, null, nonulls, offset, max, sort, dir, principal);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get species list items for a given species list ID (legacy API).
     * 
     * @param speciesListIDs
     * @param searchQuery
     * @param fields
     * @param offset
     * @param max
     * @param sort
     * @param dir
     * @param principal
     * @return
     */
    private ResponseEntity<Object> getLegacySpeciesListItems(String speciesListIDs, String searchQuery, String fields, Boolean nonulls,
            Integer offset, Integer max, String sort, String dir, Principal principal) {
        // convert max and offset to page and pageSize
        int[] pageAndSize = calculatePageAndSize(offset, max);
        int page = pageAndSize[0];
        int pageSize = pageAndSize[1];
        logger.debug("Fetching legacy species list items for speciesListIDs: {} with offset: {}, max: {}", speciesListIDs, offset, max);
        logger.debug("Calculated page and pageSize: {} with page: {}, pageSize: {}", speciesListIDs, page, pageSize);
        List<SpeciesListItem> speciesListItems;
        
        try {
            speciesListItems = searchHelperService.fetchSpeciesListItems(speciesListIDs, searchQuery, fields, nonulls, page, pageSize, sort, dir, principal);
        } catch (Exception e) {
            logger.error("Error fetching species list items: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.name(), e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        }

        if (speciesListItems.isEmpty()) {
                // Return 200 with empty array for legacy compatibility
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }

        List<SpeciesListItemVersion1> legacySpeciesListItems = legacyService.convertListItemToVersion1(speciesListItems);

        return new ResponseEntity<>(legacySpeciesListItems, HttpStatus.OK);
    }

    @Operation(
            tags = "REST v1",
            summary = "Get a list of keys from KVP common across a list multiple species lists",
            deprecated = true
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Species list found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = List.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Invalid parameters",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Species list not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping({"/v1/listCommonKeys/{speciesListIDs}", "/v1/listCommonKeys/{speciesListIDs}/", "/v1/listCommonKeys", "/v1/listCommonKeys/"})
    public ResponseEntity<Object> speciesListCommonKeys(
            @Parameter(
                    name = "speciesListIDs",
                    description = "List of species list IDs (comma-separated)",
                    example = "dr18404,dr18457",
                    schema = @Schema(type = "string")
            )
            @PathVariable(value = "speciesListIDs", required = false) String speciesListIDs,
            @Parameter(
                    name = "druid",
                    description = "List of species list IDs (comma-separated), alternative to path variable",
                    example = "dr10637"
            )
            @RequestParam(value = "druid", required = false) String druid,
            @AuthenticationPrincipal Principal principal) {
        if (speciesListIDs == null) speciesListIDs = druid;
        if (speciesListIDs == null) {
            return ResponseEntity.badRequest().body("speciesListIDs or druid parameter is required");
        }
        try {
            List<String> IDs = Arrays.stream(speciesListIDs.split(",")).toList();
            List<SpeciesList> speciesLists = speciesListMongoRepository
                    .findByDataResourceUidInOrIdIn(IDs);

            if (!speciesLists.isEmpty()) {
                List<SpeciesList> validLists = speciesLists.stream()
                        .filter(list -> !list.isPrivate() || authUtils.isAuthorized(list, principal))
                        .toList();

                if (validLists.isEmpty()) {
                    return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
                }

                Set<String> commonKeys = SearchHelperService.findCommonKeys(speciesLists);

                // Perform string replacement for some values in the Set based on a Map<String, String>
                Map<String, String> replacements = Map.of(
                    "taxonRank", "rank",
                    "rawfamily", "family"
                );

                Set<String> renamedCommonKeys = commonKeys.stream()
                        .map(key -> replacements.getOrDefault(key, key))
                        .collect(java.util.stream.Collectors.toSet());

                return new ResponseEntity<>(renamedCommonKeys, HttpStatus.OK);
            }

            // Return 200 with empty array for legacy compatibility
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(tags = "REST v1", summary = "Get a list of species lists metadata by (taxon) GUID/s", deprecated = true)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Species list items found for GUID/s",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = SpeciesItemVersion1.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "400", 
                    description = "Bad Request - Invalid parameters", 
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - user is not authorized to view this species list",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Species list not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @JsonView(Views.Wide.class)
    @GetMapping("/v1/species/**")
    public ResponseEntity<Object> speciesListItemsForGuid(
            @Parameter(
                    name = "guids",
                    description = "Species GUIDs (can be comma-separated) search on. Note this can be provided as a URL path for backwards compatibility.",
                    example = "https://biodiversity.org.au/afd/taxa/083b413f-8746-4788-8dc1-3da495d78a79",
                    schema = @Schema(type = "string")
            )
            @Nullable @RequestParam(name = "guids") String guids,
            @Parameter(
                    name = "speciesListIDs",
                    description = "Optional list of species list IDs (can be comma-separated) to filter the results.",
                    example = "dr18404,dr18457",
                    schema = @Schema(type = "string")
            )
            @Nullable @RequestParam(name = "speciesListIDs") String speciesListIDs,
            @Nullable @RequestParam(name = "page", defaultValue = "1") @Max(9990) Integer page,
            @Nullable @RequestParam(name = "pageSize", defaultValue = "9999") @Max(10000) Integer pageSize,
            @RequestParam(name = "isAuthoritative", required = false) String isAuthoritative,
            @RequestParam(name = "isThreatened", required = false) String isThreatened,
            @RequestParam(name = "isInvasive", required = false) String isInvasive,
            @RequestParam(name = "isSDS", required = false) String isSDS,
            @RequestParam(name = "isBIE", required = false) String isBIE,
            @AuthenticationPrincipal Principal principal,
            HttpServletRequest request) {
        String fullUrl = request.getRequestURL().toString();
        String guid = (fullUrl.split("/v1/species/").length > 1) ? fullUrl.split("/v1/species/")[1] : "";

        if (guid != null && guid.contains("%3A%2F")) {
            // Decode the URL-encoded GUID if it's encoded
            guid = URLDecoder.decode(guid, StandardCharsets.UTF_8);
        }

        // Catch possible null values from unboxed page and pageSize
        int pageVal = Math.max((page != null ? page : 1), 1); // Ensure page is at least 1
        int pageSizeVal = Math.max((pageSize != null ? pageSize : 9999), 1); // Ensure pageSize is at least 1

        String inputGuids = (StringUtils.isNotBlank(guid) ? guid : (StringUtils.isNotBlank(guids) ? guids : ""));

        try {
            // If any boolean list-level filters are set, resolve matching list IDs via MongoDB
            // and intersect with any caller-supplied speciesListIDs
            boolean hasBooleanFilters = StringUtils.isNotBlank(isAuthoritative)
                    || StringUtils.isNotBlank(isThreatened)
                    || StringUtils.isNotBlank(isInvasive)
                    || StringUtils.isNotBlank(isSDS)
                    || StringUtils.isNotBlank(isBIE);

            if (hasBooleanFilters) {
                RESTSpeciesListQuery speciesListQuery = new RESTSpeciesListQuery();
                fixLegacyBooleanSyntax(isAuthoritative, isThreatened, isInvasive, isSDS, isBIE, null, speciesListQuery);

                AlaUserProfile profile = authUtils.getUserProfile(principal);
                String userId = profile != null ? profile.getUserId() : null;
                Boolean isAdmin = authUtils.hasAdminRole(profile);

                // Fetch all matching lists (up to 10,000) — no text search, just boolean filters
                Page<SpeciesList> matchingLists = searchHelperService.searchDocuments(
                        speciesListQuery.convertTo(), userId, isAdmin, ".*", PageRequest.of(0, 10000));

                String filteredListIDs = matchingLists.getContent().stream()
                        .map(list -> list.getDataResourceUid() != null ? list.getDataResourceUid() : list.getId())
                        .collect(java.util.stream.Collectors.joining(","));

                if (filteredListIDs.isEmpty()) {
                    return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
                }

                // Intersect with caller-supplied speciesListIDs if present
                if (StringUtils.isNotBlank(speciesListIDs)) {
                    Set<String> callerIDs = new java.util.HashSet<>(Arrays.asList(speciesListIDs.split(",")));
                    filteredListIDs = Arrays.stream(filteredListIDs.split(","))
                            .filter(callerIDs::contains)
                            .collect(java.util.stream.Collectors.joining(","));

                    if (filteredListIDs.isEmpty()) {
                        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
                    }
                }

                speciesListIDs = filteredListIDs;
            }

            List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(
                    inputGuids, speciesListIDs, pageVal, pageSizeVal, principal);

            if (speciesListItems.isEmpty()) {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK); // empty list
            }

            List<SpeciesItemVersion1> legacySpeciesListItems = legacyService.convertToSpeciesItemVersion1(speciesListItems);

            return new ResponseEntity<>(legacySpeciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            logger.info(e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(
            tags = "REST v1",
            summary = "Get species list(s) item details",
            description = "Get details of individual items i.e. species for specified species list(s) filter-able by specified fields",
            deprecated = true)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Species list items found for GUID/s",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = QueryListItemVersion1.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400", 
                    description = "Bad Request - Invalid parameters", 
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - user is not authorized to view this species list",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Species list not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @JsonView(Views.Narrow.class)
    @GetMapping({"/v1/queryListItemOrKVP", "/v1/queryListItemOrKVP/"})
    public ResponseEntity<Object> queryListItemOrKVP(
            @Parameter(
                name = "druid",
                description = "The data resource ID (or speciesList ID)",
                example = "dr18404",
                schema = @Schema(type = "string")
            )
            @RequestParam(name = "druid") String druid,
            @Nullable @RequestParam(name = "q") String q,
            @Nullable @RequestParam(name = "fields") String fields, // not yet implemented in service
            @Nullable @RequestParam(name = "includeKVP", defaultValue = "true") Boolean includeKVP,
            @Nullable @RequestParam(name = "nonulls", defaultValue = "false") Boolean nonulls,
            @Nullable @RequestParam(name = "offset", defaultValue = "0") Integer offset,
            @Nullable @RequestParam(name = "max", defaultValue = "10") Integer max,
            @Nullable @RequestParam(name = "sort", defaultValue="speciesListID") String sort,
            @Nullable @RequestParam(name = "order", defaultValue="asc") String order,
            @AuthenticationPrincipal Principal principal
    ) {
        try {
            if (Boolean.TRUE.equals(nonulls)) {
                // TODO: remove this code when nonulls is supported
                return ResponseEntity.badRequest().body("The 'nonulls' parameter is not yet supported.");
            }

            // convert max and offset to page and pageSize
            int[] pageAndSize = calculatePageAndSize(offset, max);
            int page = pageAndSize[0];
            int pageSize = pageAndSize[1];
            List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(druid, q, fields, null, page, pageSize, sort, order, principal);

            if (speciesListItems.isEmpty()) {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
            }

            List<QueryListItemVersion1> legacySpeciesListItems = legacyService.convertQueryListItemToVersion1(speciesListItems);
            
            return new ResponseEntity<>(legacySpeciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Calculate the page and size for Spring Data Pageable pagination 
     * from legacy offset and max parameters.
     *
     * @param offset the offset to start from
     * @param max the maximum number of items to return
     * @return an array containing the page number and page size
     */
    private static int[] calculatePageAndSize(@Nullable Integer offset, @Nullable Integer max) {
        int page = ((offset != null ? offset : 0) / (max != null ? max : 10)); // was + 1 for 1-based page, but Pageable is zero-based
        int pageSize = (max != null ? max : 10);
        logger.debug("Calculated page and pageSize: page: {}, pageSize: {}", page, pageSize);
        return new int[]{page, pageSize};
    }
}
