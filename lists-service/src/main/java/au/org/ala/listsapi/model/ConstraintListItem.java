package au.org.ala.listsapi.model;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ConstraintListItem {
  private String value;
  private String label;

  public ConstraintListItem(String value, String label) {
    this.value = value;
    this.label = label;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }
}
