package au.org.ala.listsapi.model;

import java.io.Serializable;
import java.util.List;
import lombok.NoArgsConstructor;

/**
 * Model class representing a species list page model This POJO is used for controller response
 * serialization only.
 */
@NoArgsConstructor
public class SpeciesListPage implements Serializable {

  private List<SpeciesList> lists;
  private long listCount;
  private int offset;
  private int max;

  public SpeciesListPage(List<SpeciesList> lists, long listCount, int offset, int max) {
    this.lists = lists;
    this.listCount = listCount;
    this.offset = offset;
    this.max = max;
  }

  public List<SpeciesList> getLists() {
    return lists;
  }

  public void setLists(List<SpeciesList> lists) {
    this.lists = lists;
  }

  public long getListCount() {
    return listCount;
  }

  public void setListCount(long listCount) {
    this.listCount = listCount;
  }

  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  public int getMax() {
    return max;
  }

  public void setMax(int max) {
    this.max = max;
  }
}
