package au.org.ala.listsapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class Filter {
  private String key;
  private String value;
}
