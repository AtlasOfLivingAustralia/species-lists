package au.org.ala.listsapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestJob {
  private List<String> fieldList;
  private List<String> originalFieldNames;
  private List<String> facetList;
  private int rowCount = 0;
  private long distinctMatchCount = 0;
  private String localFile;
  private List<String> validationErrors;

  public IngestJob() {}

  public IngestJob(
      List<String> fieldList,
      List<String> originalFieldNames,
      List<String> facetList,
      int rowCount,
      long distinctMatchCount,
      String localFile,
      List<String> validationErrors) {
    this.fieldList = fieldList;
    this.originalFieldNames = originalFieldNames;
    this.facetList = facetList;
    this.rowCount = rowCount;
    this.distinctMatchCount = distinctMatchCount;
    this.localFile = localFile;
    this.validationErrors = validationErrors;
  }

  public List<String> getFieldList() {
    return fieldList;
  }

  public void setFieldList(List<String> fieldList) {
    this.fieldList = fieldList;
  }

  public List<String> getOriginalFieldNames() {
    return originalFieldNames;
  }

  public void setOriginalFieldNames(List<String> originalFieldNames) {
    this.originalFieldNames = originalFieldNames;
  }

  public List<String> getFacetList() {
    return facetList;
  }

  public void setFacetList(List<String> facetList) {
    this.facetList = facetList;
  }

  public int getRowCount() {
    return rowCount;
  }

  public void setRowCount(int rowCount) {
    this.rowCount = rowCount;
  }

  public long getDistinctMatchCount() {
    return distinctMatchCount;
  }

  public void setDistinctMatchCount(long distinctMatchCount) {
    this.distinctMatchCount = distinctMatchCount;
  }

  public String getLocalFile() {
    return localFile;
  }

  public void setLocalFile(String localFile) {
    this.localFile = localFile;
  }

  public List<String> getValidationErrors() {
    return validationErrors;
  }

  public void setValidationErrors(List<String> validationErrors) {
    this.validationErrors = validationErrors;
  }

  @Override
  public String toString() {
    return "IngestJob{"
        + "fieldList="
        + fieldList
        + ", originalFieldNames="
        + originalFieldNames
        + ", facetList="
        + facetList
        + ", rowCount="
        + rowCount
        + ", distinctMatchCount="
        + distinctMatchCount
        + ", localFile='"
        + localFile
        + '\''
        + ", validationErrors="
        + validationErrors
        + '}';
  }
}
