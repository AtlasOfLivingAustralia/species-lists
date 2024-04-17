package au.org.ala.listsapi.service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import au.org.ala.listsapi.model.ConstraintListItem;
import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.model.InputSpeciesList;
import au.org.ala.listsapi.model.SpeciesList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ConstraintService {
  private static final Logger logger = LoggerFactory.getLogger(ConstraintService.class);

  @Qualifier("webApplicationContext")
  @Autowired private ResourceLoader resourceLoader;

  private Map<String, List<ConstraintListItem>> constraints = null;

  private String getJson() {
    String out = null;
    Resource resource = resourceLoader.getResource("classpath:/constraints.json");

    // Don't attempt to read the resource content if it does not exist
    if (!resource.exists()) return null;

    try {
      // Read the content of the JSON file
      byte[] bytes = Files.readAllBytes(Paths.get(resource.getURI()));
      out = new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      logger.error(ex.getMessage());
    }

    return out;
  }

  @PostConstruct
  private void init() {
    Gson gson = new Gson();
    String json = getJson();

    Type constraintType = new TypeToken<Map<String, List<ConstraintListItem>>>(){}.getType();
    constraints = gson.fromJson(json, constraintType);
  }

  // This function returns the constraint list as a map of List<ConstraintListItem> to correctly output JSON
  public Map<String, List<ConstraintListItem>> getConstraintMap() {
    return constraints;
  }

  public boolean validateValue(ConstraintType constraintType, String value) {
    List<ConstraintListItem> list = constraints.get(constraintType.name());

    return list.stream()
            .filter(elm -> elm.getValue().equals(value))
            .findAny().orElse(null) != null;
  }

  public boolean validateList(InputSpeciesList speciesList) {
    // check that the supplied list type, region and license is valid
    return !(
            validateValue(ConstraintType.lists, speciesList.getListType()) &&
            validateValue(ConstraintType.regions, speciesList.getRegion()) &&
            validateValue(ConstraintType.licenses, speciesList.getLicence())
    );
  }

  public List<ConstraintListItem>  getConstraintList(ConstraintType constraintType) throws Exception {
    List<ConstraintListItem> list = constraints.get(constraintType.name());

    if (list == null) {
      throw new Exception("Could not find corresponding constraint list for '" + constraintType + "' type!");
    }

    return list;
  }
}
