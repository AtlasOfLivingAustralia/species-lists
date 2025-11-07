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

import au.org.ala.listsapi.model.ErrorResponse;
import au.org.ala.listsapi.model.QueryListItemVersion1;
import au.org.ala.listsapi.model.RESTSpeciesListQuery;
import au.org.ala.listsapi.model.SpeciesList;
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
            Integer page = offset / max; // zero indexed, as required by Pageable
            Pageable paging = PageRequest.of(page, max);
            RESTSpeciesListQuery speciesListQuery = new RESTSpeciesListQuery();
            fixLegacyBooleanSyntax(isAuthoritative, isThreatened, isInvasive, isSDS, isBIE, druid, speciesListQuery);
            
            if (StringUtils.isNotBlank(sort)) {
                paging = PageRequest.of(page, max,
                        "asc".equalsIgnoreCase(order)
                                ? org.springframework.data.domain.Sort.by(fixSortField(sort)).ascending()
                                : org.springframework.data.domain.Sort.by(fixSortField(sort)).descending());
            }

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
            "guid", "taxonID",
            "region", "hasRegion"
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

    @SecurityRequirement(name = "JWT")
    @Operation(tags = "REST v1", summary = "Search for species list items", deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Species lists items found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = List.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid parameters", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user is not authorized to view this species list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            // @ApiResponse(responseCode = "404", description = "Species list not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
    })
    @GetMapping("/v1/speciesListItems")
    public ResponseEntity<Object> speciesListItemsSearch(
            @Nullable @RequestParam(name = "isAuthoritative") String isAuthoritative,
            @Nullable @RequestParam(name = "isThreatened") String isThreatened,
            @Nullable @RequestParam(name = "isInvasive") String isInvasive,
            @Nullable @RequestParam(name = "isSDS") String isSDS,
            @Nullable @RequestParam(name = "isBIE") String isBIE,
            @Nullable @RequestParam(name = "druid") String druid,
            @Parameter(description = "Query string (q)")
            @Nullable @RequestParam(name = "q") String query,
            @Parameter(description = "Sort field")
            @Schema(allowableValues = {"count", "speciesListName", "speciesListID", "listType", "dateCreated", "lastUpdated", "owner", "region", "category", "authority", "guid"})
            @RequestParam(name = "sort", defaultValue = "speciesListID", required = false) String sort,
            @Parameter(description = "Sort direction")
            @Schema(allowableValues = {"asc", "desc"})
            @RequestParam(name = "order", defaultValue = "asc") String order,
            @RequestParam(name = "max", defaultValue = "25", required = false) @Max(10000) int max,
            @RequestParam(name = "offset", defaultValue = "0", required = false) @Max(9990) int offset,
            @AuthenticationPrincipal Principal principal) {
        try {
            Integer page = offset / max; // zero indexed, as required by Pageable
            Pageable paging = PageRequest.of(0, 10000); // we want all matching lists, paging will be applied to items later
            RESTSpeciesListQuery speciesListQuery = new RESTSpeciesListQuery();
            fixLegacyBooleanSyntax(isAuthoritative, isThreatened, isInvasive, isSDS, isBIE, druid, speciesListQuery);

            SpeciesList convertedSpeciesListQuery = speciesListQuery.convertTo();
            AlaUserProfile profile = authUtils.getUserProfile(principal);
            String userId = profile != null ? profile.getUserId() : null;
            Boolean isAdmin = authUtils.hasAdminRole(profile);
            query = StringUtils.isNotBlank(query) ? URLDecoder.decode(query, StandardCharsets.UTF_8) : ""; // regex for all if blank
            
            // First, get the matching species lists
            Page<SpeciesList> speciesLists = searchHelperService.searchDocuments(convertedSpeciesListQuery, userId, isAdmin, query, paging);
            
            if (speciesLists.isEmpty()) {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
            }
            
            // Extract the list IDs (data resource UIDs)
            String speciesListIDs = speciesLists.getContent().stream()
                    .map(list -> list.getDataResourceUid() != null ? list.getDataResourceUid() : list.getId())
                    .collect(java.util.stream.Collectors.joining(","));
            
            // Map sort field for items (fix for lergacy sort fields)
            String itemSort = sort != null ? fixSortField(sort) : "speciesListID";
            
            // Fetch all items from the matching lists
            List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(
                    speciesListIDs, 
                    query != null ? query : null,  // no additional search query on items
                    null,  // no field filtering
                    page + 1,  // searchHelperService uses 1-based pagination
                    max, 
                    itemSort,  // use item-appropriate sort field
                    order, 
                    principal);
            
            if (speciesListItems.isEmpty()) {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
            }
            
            // Convert to legacy format
            List<SpeciesListItemVersion1> legacySpeciesListItems = legacyService.convertListItemToVersion1(speciesListItems);
            
            return new ResponseEntity<>(legacySpeciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error occurred for /v1/speciesListItems: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
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

        if (speciesList.isPresent() && speciesList.get().getIsPrivate() && !authUtils.isAuthorized(speciesList.get(), principal)) {
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

    @Operation(tags = "REST v1", summary = "Get species list items for a given species list ID", deprecated = true)
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
    @GetMapping({"/v1/speciesListItems/{druid}", "/v1/speciesListItems/{druid}/"})
    public ResponseEntity<Object> speciesListItems(
            @Parameter(
                    name = "druid",
                    description = "The data resource id (or speciesListID) or comma separated ids to identify list(s) to return list items for e.g. '/v1/speciesListItems/dr123,dr456,dr789'",
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
            @Nullable @RequestParam(name = "fields") String fields,
            @Nullable @RequestParam(name = "offset", defaultValue = "0") @Max(10001) Integer offset,
            @Nullable @RequestParam(name = "max", defaultValue = "10") @Max(10000) Integer max,
            @Nullable @RequestParam(name = "sort", defaultValue="speciesListID") String sort,
            @Nullable @RequestParam(name = "dir", defaultValue="asc") String dir,
            @AuthenticationPrincipal Principal principal) {
        try {
            return getLegacySpeciesListItems(speciesListIDs, searchQuery, fields, offset, max, sort, dir, principal);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
    @SecurityRequirement(name = "JWT")
    @GetMapping({"/v1/speciesListItemsInternal/{druid}", "/v1/speciesListItemsInternal/{druid}/"})
    public ResponseEntity<Object> speciesListInternal(
            @Parameter(
                    name = "druid",
                    description = "The data resource id (or speciesListID) or comma separated ids to identify list(s) to return list items for e.g. '/v1/speciesListItemsInternal/dr123,dr456,dr789'",
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
            @Nullable @RequestParam(name = "fields") String fields,
            @Nullable @RequestParam(name = "offset", defaultValue = "0") @Max(10001) Integer offset,
            @Nullable @RequestParam(name = "max", defaultValue = "10") @Max(10000) Integer max,
            @Nullable @RequestParam(name = "sort", defaultValue="speciesListID") String sort,
            @Nullable @RequestParam(name = "dir", defaultValue="asc") String dir,
            @AuthenticationPrincipal Principal principal) {
        try {
            if (!authUtils.isAuthorized(principal)) {
                ErrorResponse errorResponse = new ErrorResponse(HttpStatus.FORBIDDEN.name(), "Not authorised to access this endpoint", HttpStatus.FORBIDDEN.value());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorResponse);
            }

            return getLegacySpeciesListItems(speciesListIDs, searchQuery, fields, offset, max, sort, dir, principal);
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
    private ResponseEntity<Object> getLegacySpeciesListItems(String speciesListIDs, String searchQuery, String fields,
            Integer offset, Integer max, String sort, String dir, Principal principal) {
        // convert max and offset to page and pageSize
        int[] pageAndSize = calculatePageAndSize(offset, max);
        int page = pageAndSize[0];
        int pageSize = pageAndSize[1];
        logger.debug("Fetching legacy species list items for speciesListIDs: {} with offset: {}, max: {}", speciesListIDs, offset, max);
        logger.debug("Calculated page and pageSize: {} with page: {}, pageSize: {}", speciesListIDs, page, pageSize);
        List<SpeciesListItem> speciesListItems;
        
        try {
            speciesListItems = searchHelperService.fetchSpeciesListItems(speciesListIDs, searchQuery, fields, page, pageSize, sort, dir, principal);
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
    @GetMapping({"/v1/listCommonKeys/{speciesListIDs}", "/v1/listCommonKeys/{speciesListIDs}/"})
    public ResponseEntity<Object> speciesListCommonKeys(
            @Parameter(
                    name = "speciesListIDs",
                    description = "List of species list IDs (comma-separated)",
                    schema = @Schema(type = "string")
            )
            @PathVariable("speciesListIDs") String speciesListIDs,
            @AuthenticationPrincipal Principal principal) {
        try {
            List<String> IDs = Arrays.stream(speciesListIDs.split(",")).toList();
            List<SpeciesList> speciesLists = speciesListMongoRepository
                    .findAllByDataResourceUidIsInOrIdIsIn(IDs, IDs);

            if (!speciesLists.isEmpty()) {
                List<SpeciesList> validLists = speciesLists.stream()
                        .filter(list -> !list.getIsPrivate() || authUtils.isAuthorized(list, principal))
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
                            schema = @Schema(implementation = SpeciesListItemVersion1.class)
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
    @GetMapping("/v1/species/**")
    public ResponseEntity<Object> speciesListItemsForGuid(
            @Parameter(
                    name = "guids",
                    description = "Species GUIDs (can be comma-separated) search on. Note this can be provided as a URL path for backwards compatibility.",
                    schema = @Schema(type = "string")
            )
            @Nullable @RequestParam(name = "guids") String guids,
            @Parameter(
                    name = "speciesListIDs",
                    description = "Optional list of species list IDs (can be comma-separated) to filter the results.",
                    schema = @Schema(type = "string")
            )
            @Nullable @RequestParam(name = "speciesListIDs") String speciesListIDs,
            @Nullable @RequestParam(name = "page", defaultValue = "1") @Max(9990) Integer page,
            @Nullable @RequestParam(name = "pageSize", defaultValue = "9999") @Max(10000) Integer pageSize,
            @AuthenticationPrincipal Principal principal,
            HttpServletRequest request) {
        String fullUrl = request.getRequestURL().toString();
        String guid = (fullUrl.split("/v1/species/").length > 1) ? fullUrl.split("/v1/species/")[1] : "";

        if (guid != null && guid.contains("%3A%2F")) {
            // Decode the URL-encoded GUID if it's encoded
            guid = URLDecoder.decode(guid, StandardCharsets.UTF_8);
        }

        String inputGuids = (StringUtils.isNotBlank(guid) ? guid : (StringUtils.isNotBlank(guids) ? guids : ""));
        // Catch possible null values from unboxed page and pageSize
        int pageVal = Math.max((page != null ? page : 1), 1); // Ensure page is at least 1
        int pageSizeVal = Math.max((pageSize != null ? pageSize : 9999), 1); // Ensure pageSize is at least 1

        try {
            List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(inputGuids, speciesListIDs, pageVal, pageSizeVal, principal);

            if (speciesListItems.isEmpty()) {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK); // empty list
            }

            List<SpeciesListItemVersion1> legacySpeciesListItems = legacyService.convertListItemToVersion1(speciesListItems);

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
    @GetMapping({"/v1/queryListItemOrKVP", "/v1/queryListItemOrKVP/"})
    public ResponseEntity<Object> queryListItemOrKVP(
            @RequestParam(name = "druid") String druid,
            @Nullable @RequestParam(name = "q") String q,
            @Nullable @RequestParam(name = "fields") String fields,
            @Nullable @RequestParam(name = "includeKVP", defaultValue = "true") Boolean includeKVP,
            @Nullable @RequestParam(name = "nonulls", defaultValue = "false") Boolean nonulls,
            @Nullable @RequestParam(name = "offset", defaultValue = "0") @Max(9990) Integer offset,
            @Nullable @RequestParam(name = "max", defaultValue = "10") @Max(10000) Integer max,
            @Nullable @RequestParam(name = "sort", defaultValue="speciesListID") String sort,
            @Nullable @RequestParam(name = "order", defaultValue="asc") String order,
            @AuthenticationPrincipal Principal principal
    ) {
        try {
            // convert max and offset to page and pageSize
            int[] pageAndSize = calculatePageAndSize(offset, max);
            int page = pageAndSize[0];
            int pageSize = pageAndSize[1];
            List<SpeciesListItem> speciesListItems = searchHelperService.fetchSpeciesListItems(druid, q, fields, page, pageSize, sort, order, principal);

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
     * Calculate the page and size for pagination (legacy API)
     *
     * @param offset the offset to start from
     * @param max the maximum number of items to return
     * @return an array containing the page number and page size
     */
    private static int[] calculatePageAndSize(@Nullable Integer offset, @Nullable Integer max) {
        int page = ((offset != null ? offset : 0) / (max != null ? max : 10)) + 1;
        int pageSize = (max != null ? max : 10);
        logger.info("Calculated page and pageSize: page: {}, pageSize: {}", page, pageSize);
        return new int[]{page, pageSize};
    }
}
