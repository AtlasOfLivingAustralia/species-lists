package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ValidationService {

  public static final Logger log = LoggerFactory.getLogger(ValidationService.class);

  // @Value("${constraints.file}")
  // private String constraintsFile;

  @Value("${constraints.file}")
  private Resource constraintsFile;

  @Value("${userDetails.api.url}")
  private String userDetailsUrl;

  @Value("${constraints.userdetails.countries.enabled:false}")
  private boolean userDetailsCountriesEnabled;

  private Map<String, List<ConstraintListItem>> constraints = null;

  private List<ConstraintListItem> getConstraintsByKey(ConstraintType constraintType) {
    return constraints.get(constraintType.name());
  }

  private void setConstraintsByKey(ConstraintType constraintType, List<ConstraintListItem> items) {
    constraints.put(constraintType.name(), items);
  }

  @PostConstruct
  private void init() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    
    
    try (InputStream inputStream = constraintsFile.getInputStream()) {
      // 2. Use the InputStream directly with ObjectMapper
      constraints = objectMapper.readValue(inputStream, new TypeReference<HashMap<String, List<ConstraintListItem>>>() {});
      System.out.println("Constraints loaded successfully: " + constraints.size() + " categories.");

    } catch (IOException e) {
      // Handle exceptions appropriately (e.g., logging, re-throwing a custom exception)
      throw new RuntimeException("Failed to read or parse constraints file from: " + constraintsFile.getDescription(), e);
    }

    if (userDetailsCountriesEnabled) {
      try {
        List<Location> userdetailsCountries = fetchJson(userDetailsUrl + "/ws/registration/countries.json", new TypeReference<>() {});
        List<ConstraintListItem> countries = getConstraintsByKey(ConstraintType.region);

        // Map the countries list into UI constraints
        userdetailsCountries.forEach(e -> {
          var constraint = new ConstraintListItem(e.getIsoCode(), e.getName());
          countries.add(constraint);
        });

        setConstraintsByKey(ConstraintType.region, countries);
      } catch (Exception ex) {
        log.error("Error loading country constraints from userdetails", ex);
      }
    }
  }

  private <T> T fetchJson(String uri, TypeReference<T> type) throws Exception {
    HttpRequest httpRequest =
            HttpRequest.newBuilder(new URI(uri))
                    .GET()
                    .build();

    HttpResponse<String> response =
            HttpClient.newBuilder()
                    .proxy(ProxySelector.getDefault())
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(response.body(), type);
  }

  // Returns the constraint list as a map of List<ConstraintListItem> to correctly output JSON
  public Map<String, List<ConstraintListItem>> getConstraintMap() {
    return constraints;
  }

  public boolean isValueValid(ConstraintType constraintType, String value) {
    List<ConstraintListItem> list = getConstraintsByKey(constraintType);

    return list.stream()
            .filter(elm -> elm.getValue().equals(value))
            .findAny().orElse(null) != null;
  }

  public boolean isListValid(InputSpeciesList speciesList) {
    // check that the supplied list type, region and license is valid
    return (
            isValueValid(ConstraintType.listType, speciesList.getListType()) &&
            isValueValid(ConstraintType.licence, speciesList.getLicence())
    );
  }
}
