package au.org.ala.listsapi.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.org.ala.listsapi.model.ConstraintListItem;
import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.model.InputSpeciesList;
import au.org.ala.listsapi.model.SpeciesList;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConstraintService {

  @Value("${constraints.file}")
  private String constraintsFile;

  private Map<String, List<ConstraintListItem>> constraints = null;

  @PostConstruct
  private void init() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    File json = new File(constraintsFile);

    TypeReference<HashMap<String,List<ConstraintListItem>>> typeRef = new TypeReference<>() {};
    constraints = objectMapper.readValue(json, typeRef);
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

  public boolean validateList(SpeciesList speciesList) {
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
