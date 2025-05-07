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

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.SpeciesListLegacyService;
import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class LegacyController {

    private static final Logger logger = LoggerFactory.getLogger(LegacyController.class);

    @Autowired
    protected SpeciesListMongoRepository speciesListMongoRepository;

    @Autowired
    protected SpeciesListLegacyService legacyService;

    @Autowired
    protected MongoUtils mongoUtils;

    @Operation(tags = "REST v1", summary = "Get species list metadata for a given species list ID", deprecated = true)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Species list found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpeciesListVersion1.class)
                    )
            )
    })
    @GetMapping("/v1/speciesList/{speciesListID}")
    public ResponseEntity<Object> speciesList(
            @PathVariable("speciesListID") String speciesListID) {
        Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

        return speciesList
                .map(list -> ResponseEntity.<Object>ok(legacyService.convertListToVersion1(list)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(tags = "REST v1", summary = "Get species list metadata for a given species list ID", deprecated = true)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Species list found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpeciesListItemVersion1.class)
                    )
            )
    })
    @GetMapping("/v1/speciesListItems/{druid}")
    public ResponseEntity<Object> speciesListItems(
            @Parameter(
                    name = "druid",
                    description = "The data resource id (or speciesListID) or comma separated ids to identify list(s) to return list items for e.g. '/v1/speciesListItems/dr123,dr456,dr789'",
                    schema = @Schema(type = "string")
            )
            @PathVariable("druid") String speciesListIDs,
            @Parameter(
                    name = "includeKVP",
                    description = "Whether to include KVP (key value pairs) values in the returned list item. Note this is now ignored and all KVP values are returned.",
                    schema = @Schema(type = "boolean", defaultValue = "true")
            )
            @RequestParam(name = "includeKVP", defaultValue = "false") Boolean includeKVP,
            @Nullable @RequestParam(name = "q") String searchQuery,
            @Nullable @RequestParam(name = "fields") String fields,
            @Nullable @RequestParam(name = "offset", defaultValue = "0") Integer offset,
            @Nullable @RequestParam(name = "max", defaultValue = "10") Integer max,
            @Nullable @RequestParam(name = "sort", defaultValue="speciesListID") String sort,
            @Nullable @RequestParam(name = "dir", defaultValue="asc") String dir,
            @AuthenticationPrincipal Principal principal) {
        try {
            // convert max and offset to page and pageSize
            int page = ((offset != null? offset : 0) / (max != null ? max : 10)) + 1;
            int pageSize = (max != null ? max : 10);
            List<SpeciesListItem> speciesListItems = mongoUtils.fetchSpeciesListItems(speciesListIDs, searchQuery, fields, page, pageSize, sort, dir, principal);
            if (speciesListItems.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            List<SpeciesListItemVersion1> legacySpeciesListItems = legacyService.convertListItemToVersion1(speciesListItems);

            return new ResponseEntity<>(legacySpeciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(tags = "REST v1", summary = "Get a list of keys from KVP common across a list multiple species lists", deprecated = true)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Species list found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = List.class)
                    )
            )
    })
    @GetMapping("/v1/listCommonKeys/{speciesListIDs}")
    public ResponseEntity<Object> speciesListCommonKeys(
            @Parameter(
                    name = "speciesListIDs",
                    description = "List of species list IDs (comma-separated)",
                    schema = @Schema(type = "string")
            )
            @PathVariable("speciesListIDs") String speciesListIDs,
            @AuthenticationPrincipal Principal principal) {
        try {
            List<SpeciesListItem> speciesListItems = mongoUtils.fetchSpeciesListItems(speciesListIDs, null, null, 1, 9999, null, null, principal);

            if (speciesListItems.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Extract distinct keys from the properties of all species list items
            Set<String> distinctKeys = speciesListItems.stream()
                    .flatMap(item -> item.getProperties().stream()) // Flatten the properties list from all items
                    .map(KeyValue::getKey) // Extract the "key" from each KeyValue object
                    .collect(Collectors.toSet()); // Collect distinct keys into a Set

            return new ResponseEntity<>(distinctKeys, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error calling fetchSpeciesListItems() - {}", e.getMessage(), e);
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
            @Nullable @RequestParam(name = "page", defaultValue = "1") Integer page,
            @Nullable @RequestParam(name = "pageSize", defaultValue = "9999") Integer pageSize,
            @AuthenticationPrincipal Principal principal,
            HttpServletRequest request) {
        String fullUrl = request.getRequestURL().toString();
        String guid = (fullUrl.split("/v1/species/").length > 1) ? fullUrl.split("/v1/species/")[1] : "";

        if (guid != null && guid.contains("%3A%2F")) {
            // Decode the URL-encoded GUID if its encoded
            guid = URLDecoder.decode(guid, StandardCharsets.UTF_8);
        }

        logger.debug("v1 guid: {}", guid);
        try {
            List<SpeciesListItem> speciesListItems = mongoUtils.fetchSpeciesListItems((StringUtils.isNotEmpty(guid) ? guid : guids), speciesListIDs, page, pageSize, principal);

            if (speciesListItems.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            List<SpeciesListItemVersion1> legacySpeciesListItems = legacyService.convertListItemToVersion1(speciesListItems);

            return new ResponseEntity<>(legacySpeciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            logger.info(e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
