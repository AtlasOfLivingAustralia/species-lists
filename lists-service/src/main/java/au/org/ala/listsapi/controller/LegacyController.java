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

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListItemVersion1;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.SpeciesListLegacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

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
    @GetMapping("/v1/speciesList/{speciesListID}")
    public ResponseEntity<Object> speciesList(
            @PathVariable("speciesListID") String speciesListID) {
        Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

        return speciesList
                .map(list -> ResponseEntity.<Object>ok(legacyService.convertListToVersion1(list)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(tags = "REST v1", summary = "Get a list of species lists metadata by taxon ID", deprecated = true)
    @GetMapping("/v1/species")
    public ResponseEntity<Object> speciesListByTaxonID(
            @RequestParam(name = "guids") String guids,
            @Nullable @RequestParam(name = "speciesListIDs") String speciesListIDs,
            @Nullable @RequestParam(name = "page", defaultValue = "1") Integer page,
            @Nullable @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @AuthenticationPrincipal Principal principal) {
        try {
            List<SpeciesListItem> speciesListItems = mongoUtils.fetchSpeciesListItems(guids, speciesListIDs, page, pageSize, principal);

            if (speciesListItems.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            List<SpeciesListItemVersion1> legacySpeciesListItems = legacyService.convertListItemToVersion1(speciesListItems);

            return new ResponseEntity<>(legacySpeciesListItems, HttpStatus.OK);
        } catch (Exception e) {
            logger.info(e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
