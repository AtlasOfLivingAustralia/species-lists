package au.org.ala.listsapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Data
@SuperBuilder
@AllArgsConstructor
public class ConstraintListItem {
  private String value;
  private String label;
}
