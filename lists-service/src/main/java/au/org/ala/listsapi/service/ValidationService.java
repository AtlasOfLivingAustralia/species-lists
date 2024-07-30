package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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

  @Value("${constraints.file}")
  private String constraintsFile;

  @Value("${userDetails.api.url}")
  private String userDetailsUrl;

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
    File json = new File(constraintsFile);

    constraints = objectMapper.readValue(json, new TypeReference<HashMap<String, List<ConstraintListItem>>>() {});

    try {
      List<Location> userdetailsCountries = fetchJson(userDetailsUrl + "/ws/registration/countries.json", new TypeReference<>() {});
      List<ConstraintListItem> countries = getConstraintsByKey(ConstraintType.countries);

      // Map the countries list into UI constraints
      userdetailsCountries.forEach(e -> {
        var constraint = new ConstraintListItem();
        constraint.setLabel(e.getName());
        constraint.setValue(e.getIsoCode());

        countries.add(constraint);
      });

      setConstraintsByKey(ConstraintType.countries, countries);
    } catch (Exception ex) {
      log.error("Error loading country constraints from userdetails", ex);
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
            isValueValid(ConstraintType.lists, speciesList.getListType()) &&
            isValueValid(ConstraintType.licenses, speciesList.getLicence()) &&
            isValueValid(ConstraintType.countries, speciesList.getRegion())
    );
  }

  public List<ConstraintListItem> getConstraintList(ConstraintType constraintType) throws Exception {
    List<ConstraintListItem> list = getConstraintsByKey(constraintType);

    if (list == null) {
      throw new Exception("Could not find corresponding constraint list for '" + constraintType + "' type!");
    }

    return list;
  }
}
