package au.org.ala.listsapi.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class Facet {
  String key;
  List<FacetCount> counts;
}
