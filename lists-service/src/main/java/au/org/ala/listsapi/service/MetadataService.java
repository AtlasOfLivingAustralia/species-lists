package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.service.auth.WebService;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
                "resourceType", "species-list"
        );
    }

    public void setMeta(SpeciesList speciesList) throws Exception {
        String dataResourceUid = speciesList.getDataResourceUid();
        String entityUid = dataResourceUid != null ? "/" + dataResourceUid : "";

        Map response = webService.post(
                collectoryUrl + "/ws/dataResource" + entityUid,
                listToDataResourceJSON(speciesList),
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
            // location = https://collections-test.ala.org.au/ws/dataResource/dr22893
            Map<String, List<String>> headers = (Map<String, List<String>>) response.get("headers");
            String location = headers.get("location").get(0);

            if (location != null) {
                String[] locationParts = location.split("/");
                speciesList.setDataResourceUid(locationParts[locationParts.length - 1]);
            }
        }

        logger.info(response.get("resp").toString());
    }
}
