package au.org.ala.listsapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestJob {
  List<String> fieldList;
  List<String> facetList;
  int rowCount = 0;
  String localFile;
  List<String> validationErrors;
}
