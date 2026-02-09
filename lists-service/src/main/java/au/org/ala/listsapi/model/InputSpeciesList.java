package au.org.ala.listsapi.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@Schema(description = "Input specification for species list")
public class InputSpeciesList {

  @Schema(
      description = "Unique identifier (auto-generated)",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String id;

  @Schema(description = "Version of the species list", accessMode = Schema.AccessMode.READ_ONLY)
  private Integer version;

  @Schema(
      description = "Data resource UID associated with the species list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String dataResourceUid;

  @Schema(description = "Title of the species list", requiredMode = Schema.RequiredMode.REQUIRED)
  private String title;

  @Schema(
      description = "Description of the species list",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String description;

  @Schema(description = "Type of the species list", requiredMode = Schema.RequiredMode.REQUIRED)
  private String listType;

  @Schema(
      description = "Licence under which the species list is shared",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String licence;

  @Schema(
      description = "Original field list provided for the species list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private List<String> originalFieldList;

  @Schema(
      description = "Field list used in the species list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private List<String> fieldList;

  @Schema(description = "Facet list for the species list", accessMode = Schema.AccessMode.READ_ONLY)
  private List<String> facetList;

  @Schema(
      description = "Digital Object Identifier (DOI) for the species list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String doi;

  @Schema(
      description = "Number of rows in the species list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Integer rowCount;

  @Schema(description = "Authority responsible for the species list")
  private String authority;

  @Schema(description = "Category of the species list")
  private String category;

  @Schema(description = "Region associated with the species list")
  private String region;

  @Schema(description = "Well-Known Text (WKT) representation of the species list's spatial data")
  private String wkt;

  @Schema(description = "Tags associated with the species list")
  private List<String> tags;

  @Schema(description = "Indicates if the species list is authoritative", example = "true|false")
  String isAuthoritative;

  @Schema(description = "Indicates if the species list is private", example = "true|false")
  String isPrivate;

  @Schema(description = "Indicates if the species list is invasive", example = "true|false")
  String isInvasive;

  @Schema(description = "Indicates if the species list is threatened", example = "true|false")
  String isThreatened;

  @Schema(description = "Indicates if the species list is included in BIE", example = "true|false")
  String isBIE;

  @Schema(description = "Indicates if the species list is part of SDS", example = "true|false")
  String isSDS;

  @Schema(
      description = "Owner of the species list, who created the list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String owner;

  @Schema(
      description = "User who last updated the species list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String lastUpdatedBy;

  @Schema(description = "List of users who can edit the species list")
  private List<String> editors;

  @Schema(description = "List of users who can view the species list when it is private")
  private List<String> approvedViewers;

  @Schema(
      description = "Classification details of the species list",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Classification classification;

  @Schema(
      description = "Date when the species list was created",
      accessMode = Schema.AccessMode.READ_ONLY)
  @CreatedDate
  public Date dateCreated;

  @Schema(
      description = "Date when the metadata of the species list was last updated",
      accessMode = Schema.AccessMode.READ_ONLY)
  @LastModifiedDate
  public Date metadataLastUpdated;

  @Schema(
      description = "Date when the species list was last updated",
      accessMode = Schema.AccessMode.READ_ONLY)
  public Date lastUpdated;

  @Schema(
      description = "Date when the species list was last uploaded",
      accessMode = Schema.AccessMode.READ_ONLY)
  public Date lastUploaded;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getDataResourceUid() {
    return dataResourceUid;
  }

  public void setDataResourceUid(String dataResourceUid) {
    this.dataResourceUid = dataResourceUid;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getListType() {
    return listType;
  }

  public void setListType(String listType) {
    this.listType = listType;
  }

  public String getLicence() {
    return licence;
  }

  public void setLicence(String licence) {
    this.licence = licence;
  }

  public List<String> getOriginalFieldList() {
    return originalFieldList;
  }

  public void setOriginalFieldList(List<String> originalFieldList) {
    this.originalFieldList = originalFieldList;
  }

  public List<String> getFieldList() {
    return fieldList;
  }

  public void setFieldList(List<String> fieldList) {
    this.fieldList = fieldList;
  }

  public List<String> getFacetList() {
    return facetList;
  }

  public void setFacetList(List<String> facetList) {
    this.facetList = facetList;
  }

  public String getDoi() {
    return doi;
  }

  public void setDoi(String doi) {
    this.doi = doi;
  }

  public Integer getRowCount() {
    return rowCount;
  }

  public void setRowCount(Integer rowCount) {
    this.rowCount = rowCount;
  }

  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getWkt() {
    return wkt;
  }

  public void setWkt(String wkt) {
    this.wkt = wkt;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public String getIsAuthoritative() {
    return isAuthoritative;
  }

  public void setIsAuthoritative(String isAuthoritative) {
    this.isAuthoritative = isAuthoritative;
  }

  public String getIsPrivate() {
    return isPrivate;
  }

  public void setIsPrivate(String isPrivate) {
    this.isPrivate = isPrivate;
  }

  public String getIsInvasive() {
    return isInvasive;
  }

  public void setIsInvasive(String isInvasive) {
    this.isInvasive = isInvasive;
  }

  public String getIsThreatened() {
    return isThreatened;
  }

  public void setIsThreatened(String isThreatened) {
    this.isThreatened = isThreatened;
  }

  public String getIsBIE() {
    return isBIE;
  }

  public void setIsBIE(String isBIE) {
    this.isBIE = isBIE;
  }

  public String getIsSDS() {
    return isSDS;
  }

  public void setIsSDS(String isSDS) {
    this.isSDS = isSDS;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  public void setLastUpdatedBy(String lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }

  public List<String> getEditors() {
    return editors;
  }

  public void setEditors(List<String> editors) {
    this.editors = editors;
  }

  public List<String> getApprovedViewers() {
    return approvedViewers;
  }

  public void setApprovedViewers(List<String> approvedViewers) {
    this.approvedViewers = approvedViewers;
  }

  public Classification getClassification() {
    return classification;
  }

  public void setClassification(Classification classification) {
    this.classification = classification;
  }

  public Date getDateCreated() {
    return dateCreated;
  }

  public void setDateCreated(Date dateCreated) {
    this.dateCreated = dateCreated;
  }

  public Date getMetadataLastUpdated() {
    return metadataLastUpdated;
  }

  public void setMetadataLastUpdated(Date metadataLastUpdated) {
    this.metadataLastUpdated = metadataLastUpdated;
  }

  public Date getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public Date getLastUploaded() {
    return lastUploaded;
  }

  public void setLastUploaded(Date lastUploaded) {
    this.lastUploaded = lastUploaded;
  }
}
