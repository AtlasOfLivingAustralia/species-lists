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

import java.util.List;
import java.util.Map;

import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.service.auth.WebService;

@Service
public class MetadataService {
    @Autowired WebService webService;

    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);

    @Value("${app.url}")
    private String appUrl;

    @Value("${collectory.api.url}")
    private String collectoryUrl;

    private Map listToDataResourceJSON(SpeciesList speciesList) {
        return Map.of(
            "name", speciesList.getTitle(),
            "pubDescription", speciesList.getDescription(),
            "licenseType", speciesList.getLicence(),
            "websiteUrl", appUrl + "/list/" + speciesList.getId(),
            "isPrivate", speciesList.getIsPrivate() != null && speciesList.getIsPrivate() == true ? "true" : "", // Collectory expects "true" or "" (Groovy truth bug)
            "resourceType", "species-list"
        );
    }

    public void setMeta(SpeciesList speciesList) throws Exception {
        logger.info("Setting metadata in Collectory for species list: " + speciesList.getId());
        String dataResourceUid = speciesList.getDataResourceUid();
        String entityUid = dataResourceUid != null ? "/" + dataResourceUid : "";
        Map metaDataJsonMap = listToDataResourceJSON(speciesList);

        Map response = webService.post(
                collectoryUrl + "/ws/dataResource" + entityUid,
                metaDataJsonMap,
                null,
                ContentType.APPLICATION_JSON,
                true,
                false,
                null
        );

        int statusCode = (int)response.get("statusCode");
        if (statusCode < 200 || statusCode > 299) {
            logger.error(response.get("error").toString());
            throw new Exception("Failed to create metadata entry for species list");
        }

        if (speciesList.getDataResourceUid() == null) {
            // The dataResourceUid is returned via the location header as a URL, i.e.
            // location = https://collections.test.ala.org.au/ws/dataResource/dr22893
            Map<String, List<String>> headers = (Map<String, List<String>>) response.get("headers");
            String location = headers.get("location").get(0);

            if (location != null) {
                String[] locationParts = location.split("/");
                speciesList.setDataResourceUid(locationParts[locationParts.length - 1]);
            }
        }

        logger.info(response.get("resp").toString());
    }

    public void deleteMeta(SpeciesList speciesList) throws Exception {
        String dataResourceUid = speciesList.getDataResourceUid();
        if (dataResourceUid == null || dataResourceUid.isEmpty()) {
            return;
        }

        Map response = webService.delete(
                collectoryUrl + "/ws/dataResource/" + dataResourceUid,
                null,
                ContentType.APPLICATION_JSON,
                true,
                false,
                null
        );

        int statusCode = (int)response.get("statusCode");
        if (statusCode < 200 || statusCode > 299) {
            logger.error(response.get("error").toString());
            throw new Exception("Failed to delete metadata entry for species list");
        }

        logger.info("collections DELETE: " + response.get("resp").toString());
    }
}
