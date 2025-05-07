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

package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.util.SpeciesListTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for providing legacy compatibility for species lists.
 */
@Service
public class SpeciesListLegacyService {
    @Autowired
    SpeciesListTransformer speciesListTransformer;

    /**
     * Converts a SpeciesList to the legacy SpeciesListVersion1 format.
     *
     * @param speciesList The modern SpeciesList object
     * @return The legacy SpeciesListVersion1 representation
     */
    public SpeciesListVersion1 convertListToVersion1(SpeciesList speciesList) {
        return speciesListTransformer.transformToVersion1(speciesList);
    }

    /**
     * Converts a list of SpeciesList objects to legacy SpeciesListVersion1 format.
     *
     * @param speciesLists List of modern SpeciesList objects
     * @return List of legacy SpeciesListVersion1 representations
     */
    public List<SpeciesListVersion1> convertListToVersion1(List<SpeciesList> speciesLists) {
        return speciesLists.stream()
                .map(speciesListTransformer::transformToVersion1)
                .collect(Collectors.toList());
    }

    /**
     * Converts a list of SpeciesListItem to the legacy SpeciesListItemVersion1 format.
     *
     * @param speciesListItems List of modern SpeciesListItem objects
     * @return List of legacy SpeciesListItemVersion1 representations
     */
    public List<SpeciesListItemVersion1> convertListItemToVersion1(List<SpeciesListItem> speciesListItems) {
        return speciesListItems.stream()
                .map(speciesListTransformer::transformToVersion1)
                .collect(Collectors.toList());
    }

    /**
     * Converts a single SpeciesListItem to the legacy SpeciesListItemVersion1 format.
     *
     * @param queryListItems The modern SpeciesListItem object
     * @return The legacy SpeciesListItemVersion1 representation
     */
    public List<QueryListItemVersion1> convertQueryListItemToVersion1(List<SpeciesListItem> queryListItems) {
        return queryListItems.stream()
                .map(speciesListTransformer::transformToQueryListVersion1)
                .collect(Collectors.toList());
    }
}