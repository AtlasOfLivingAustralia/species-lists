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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import au.org.ala.listsapi.model.AbbrListVersion1;
import au.org.ala.listsapi.model.KvpValueVersion1;
import au.org.ala.listsapi.model.QueryListItemVersion1;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListItemVersion1;
import au.org.ala.listsapi.model.SpeciesListVersion1;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.UserdetailsService;

/**
 * Transformer utility to convert a SpeciesList to a legacy SpeciesListVersion1 format
 * or a SpeciesListItem to a legacy SpeciesListItemVersion1 format.
 * This maintains backward compatibility with legacy applications via `/v1` endpoints.
 */
@Component
public class SpeciesListTransformer {
    private static final Logger logger = LoggerFactory.getLogger(SpeciesListTransformer.class);
    private final UserdetailsService userdetailsService;
    private final SpeciesListMongoRepository speciesListMongoRepository;

    @Autowired
    public SpeciesListTransformer(UserdetailsService userdetailsService, SpeciesListMongoRepository speciesListMongoRepository) {
        this.userdetailsService = userdetailsService;
        this.speciesListMongoRepository = speciesListMongoRepository;
    }

    @Value("${legacy.lookup.users.enabled:false}")
    private Boolean legacyLookupUsersEnabled;

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
        version1.setId(speciesList.getId());
        version1.setDataResourceUid(speciesList.getDataResourceUid());
        version1.setDateCreated(speciesList.getDateCreated());
        version1.setIsAuthoritative(speciesList.getIsAuthoritative());
        version1.setIsInvasive(speciesList.getIsInvasive());
        version1.setIsThreatened(speciesList.getIsThreatened());
        version1.setIsPrivate(speciesList.getIsPrivate());
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
        version1.setRegion(speciesList.getRegion());
        version1.setSdsType(null); // Not implemented in SpeciesList
        version1.setGeneralisation(null); // Not implemented in SpeciesList
        version1.setWkt(speciesList.getWkt());

        // Fetch user details using the userdetailsService if enabled, and the owner is not an email address (e.g., a userID number)
        if (legacyLookupUsersEnabled && speciesList.getOwner() != null && !speciesList.getOwner().isEmpty()
                && !speciesList.getOwner().contains("@")) {
            Map userDetails = userdetailsService.fetchUserByEmail(speciesList.getOwner());

            if (userDetails != null && userDetails.get("email") != null) {
                version1.setUsername((String) userDetails.get("email"));
                version1.setFullName((String) userDetails.get("displayName"));
            } else {
                version1.setUsername(speciesList.getOwner());
                version1.setFullName(speciesList.getOwnerName());
            }
        } else {
            version1.setUsername(speciesList.getOwner());
            version1.setFullName(speciesList.getOwnerName());
        }

        return version1;
    }

    /**
     * Transforms a SpeciesListItem object to a SpeciesListItemVersion1 object
     *
     * @param speciesListItem The source SpeciesListItem object
     * @return A new SpeciesListItemVersion1 object populated with values from the source
     */
    public SpeciesListItemVersion1 transformToVersion1(SpeciesListItem speciesListItem) {
        if (speciesListItem == null) {
            return null;
        }

        SpeciesListItemVersion1 listItemVersion1 = new SpeciesListItemVersion1();
        String speciesListID = speciesListItem.getSpeciesListID();
        // Map properties from SpeciesList to SpeciesListVersion1
        listItemVersion1.setId(speciesListItem.getId().toString());
        listItemVersion1.setSpeciesListID(speciesListID);
        listItemVersion1.setLsid(speciesListItem.getClassification().getTaxonConceptID());
        listItemVersion1.setScientificName(speciesListItem.getScientificName());
        listItemVersion1.setCommonName(speciesListItem.getVernacularName() == null ? speciesListItem.getClassification().getVernacularName() : speciesListItem.getVernacularName());
        listItemVersion1.setName(speciesListItem.getClassification().getScientificName());
        listItemVersion1.setDataResourceUid(speciesListID); // fallback - attempt to set actual DataResourceUid further down

        // Get list details via MongoDB
        Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

        AbbrListVersion1 list = new AbbrListVersion1();

        if (speciesList.isPresent()) {
            SpeciesList speciesListV2 = speciesList.get();
            list.setListName(speciesListV2.getTitle());
            list.setUsername(speciesListV2.getOwner());
            list.setSds(speciesListV2.getIsSDS());
            list.setIsBIE(speciesListV2.getIsBIE());
            listItemVersion1.setList(list);
            listItemVersion1.setDataResourceUid(speciesListV2.getDataResourceUid() == null ? speciesListID : speciesListV2.getDataResourceUid());
        } else {
            logger.warn("SpeciesListItemVersion1 transformToVersion1() -> Species list not found for ID: " + speciesListID);
        }

        List<KvpValueVersion1> kvps = new ArrayList<>();
        speciesListItem.getProperties()
                .forEach(kvpValue -> kvps.add(new KvpValueVersion1(kvpValue.getKey(), kvpValue.getValue())));
        listItemVersion1.setKvpValues(kvps);

        return listItemVersion1;
    }

    /**
     * Transforms a SpeciesListItem object to a QueryListItemVersion1 object
     *
     * @param speciesListItem The source SpeciesListItem object
     * @return A new QueryListItemVersion1 object populated with values from the source
     */
    public QueryListItemVersion1 transformToQueryListVersion1(SpeciesListItem speciesListItem) {
        if (speciesListItem == null) {
            return null;
        }

        QueryListItemVersion1 queryListItemV1 = new QueryListItemVersion1();
        String speciesListID = speciesListItem.getSpeciesListID();

        // Map properties from SpeciesList to SpeciesListVersion1
        queryListItemV1.setId(speciesListItem.getId().toString());
        queryListItemV1.setSpeciesListID(speciesListID);
        queryListItemV1.setDataResourceUid(speciesListID); // fallback - attempt to set actual DataResourceUid via lookup, below
        queryListItemV1.setLsid(speciesListItem.getClassification().getTaxonConceptID());
        queryListItemV1.setScientificName(speciesListItem.getScientificName());
        queryListItemV1.setCommonName(speciesListItem.getVernacularName() == null ? speciesListItem.getClassification().getVernacularName() : speciesListItem.getVernacularName());

        // Get extra details via MongoDB lookup
        Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

        List<KvpValueVersion1> kvps = new ArrayList<>();
        speciesListItem.getProperties()
                .forEach(kvpValue -> kvps.add(new KvpValueVersion1(kvpValue.getKey(), kvpValue.getValue())));
        queryListItemV1.setKvpValues(kvps);

        if (speciesList.isPresent()) {
            SpeciesList speciesListV2 = speciesList.get();
            queryListItemV1.setDataResourceUid(speciesListV2.getDataResourceUid() == null ? speciesListID : speciesListV2.getDataResourceUid());
            queryListItemV1.setName(speciesListV2.getTitle());
        } else {
            logger.warn("QueryListItemVersion1 transformToQueryListVersion1() -> Species list not found for ID: " + speciesListID);
        }

        return queryListItemV1;
    }
}