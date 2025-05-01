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

package au.org.ala.listsapi.util;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListVersion1;
import au.org.ala.listsapi.service.UserdetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transformer utility to convert a SpeciesList to a legacy SpeciesListVersion1 format.
 * This maintains backward compatibility with legacy applications.
 */
@Component
public class SpeciesListTransformer {
    private final UserdetailsService userdetailsService;

    @Autowired
    public SpeciesListTransformer(UserdetailsService userdetailsService) {
        this.userdetailsService = userdetailsService;
    }

    /**
     * Transforms a SpeciesList object to a SpeciesListVersion1 object.
     *
     * @param speciesList The source SpeciesList object
     * @return A new SpeciesListVersion1 object populated with values from the source
     */
    public SpeciesListVersion1 transformToVersion1(SpeciesList speciesList) {
        if (speciesList == null) {
            return null;
        }

        SpeciesListVersion1 version1 = new SpeciesListVersion1();

        // Map properties from SpeciesList to SpeciesListVersion1
        version1.setId(speciesList.getId().toString());
        version1.setDataResourceUid(speciesList.getDataResourceUid());
        version1.setDateCreated(speciesList.getDateCreated());
        version1.setIsAuthoritative(speciesList.getIsAuthoritative());
        version1.setIsInvasive(speciesList.getIsInvasive());
        version1.setIsThreatened(speciesList.getIsThreatened());
        version1.setIsSDS(speciesList.getIsSDS());
        version1.setIsBIE(speciesList.getIsBIE());
        version1.setItemCount(speciesList.getRowCount());

        version1.setDateCreated(speciesList.getDateCreated());
        version1.setLastUploaded(speciesList.getLastUploaded());
        version1.setLastMatched(speciesList.getLastUpdated()); // Not strictly true but good enough for legacy services
        version1.setLastUpdated(speciesList.getLastUpdated());

        version1.setListName(speciesList.getTitle());
        version1.setDescription(speciesList.getDescription());
        version1.setListType(speciesList.getListType());

        // Fetch user details using the userdetailsService
        if (speciesList.getOwner() != null && !speciesList.getOwner().isEmpty()) {
            Map userDetails = userdetailsService.fetchUserByEmail(speciesList.getOwner());

            if (userDetails != null && userDetails.get("email") != null) {
                version1.setUsername((String) userDetails.get("email"));
                version1.setFullName((String) userDetails.get("displayName"));
            } else {
                version1.setUsername(speciesList.getOwner());
                version1.setFullName(speciesList.getOwnerName());
            }
        }

        return version1;
    }
}