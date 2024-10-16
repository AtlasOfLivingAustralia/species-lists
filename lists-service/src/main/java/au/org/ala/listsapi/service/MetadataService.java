package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.InputSpeciesList;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

@Service
public class MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);
    String collectoryUrl;

    public MetadataService(
            @Value("${collectory.api.url:https://api.test.ala.org.au/metadata}") String collectoryUrl) {

        this.collectoryUrl = collectoryUrl;
    }

    private String listToDataResourceJSON(InputSpeciesList speciesList) throws JsonProcessingException {
        Map<String, String> dataResource = Map.of(
                "name", speciesList.getTitle(),
                "pubDescription", speciesList.getDescription(),
                "licenseType", speciesList.getLicence()
        );

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(dataResource);
    }

    private void setMeta(InputSpeciesList speciesList) {
        try {
            String dataResourceUid = speciesList.getDataResourceUid();
            String entityUid = dataResourceUid != null ? "/" + dataResourceUid : "";
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(listToDataResourceJSON(speciesList));

            HttpRequest httpRequest =
                    HttpRequest.newBuilder(new URI(collectoryUrl + "/ws/dataResource" + entityUid))
                            .POST(body)
                            .header("Content-Type", "application/json")
                            .build();



        } catch (Exception ex) {
            logger.error("Failed to create metadata entry for '" + speciesList.getTitle() + "' species list");
        }
    }
}
