package au.org.ala.listsapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Date;
import java.util.List;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "species_list")
public class SpeciesList {

  @Id private String id;

  @Version private Integer version;

  private String dataResourceUid;
  private String title;
  private String description;
  private String listType;
  private String licence;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> originalFieldList;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> fieldList;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> facetList;

  private String doi;
  private Integer rowCount;
  private Long distinctMatchCount;
  private String authority;
  private String category;
  private String region;

  @Formula("(CASE WHEN region IS NOT NULL AND region <> '' THEN true ELSE false END)")
  private Boolean hasRegion;

  @Column(columnDefinition = "geometry(Geometry,4326)")
  private Geometry wkt;

  Boolean isVersioned;
  Boolean isAuthoritative;
  Boolean isPrivate;
  Boolean isInvasive;
  Boolean isThreatened;
  @Column(name = "is_bie")
  Boolean isBIE;
  @Column(name = "is_sds")
  Boolean isSDS;

  private String owner; // user id of who created the list
  private String ownerName; // name of who created the list
  private String lastUpdatedBy;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> editors; // who can edit the list

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> approvedViewers; // who can view the list (when list is private)

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> tags; // list of tags

  @JdbcTypeCode(SqlTypes.JSON)
  private Classification classification; // who created the list

  @CreatedDate public Date dateCreated;
  @LastModifiedDate public Date metadataLastUpdated;
  public Date lastUpdated;
  public Date lastUploaded;

  // Manual Getters and Setters
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

  public Long getDistinctMatchCount() {
    return distinctMatchCount;
  }

  public void setDistinctMatchCount(Long distinctMatchCount) {
    this.distinctMatchCount = distinctMatchCount;
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

  public Boolean getHasRegion() {
    return hasRegion;
  }

  public Boolean getIsVersioned() {
    return isVersioned;
  }

  public void setIsVersioned(Boolean isVersioned) {
    this.isVersioned = isVersioned;
  }

  public Boolean getIsAuthoritative() {
    return isAuthoritative;
  }

  public void setIsAuthoritative(Boolean isAuthoritative) {
    this.isAuthoritative = isAuthoritative;
  }

  public Boolean getIsPrivate() {
    return isPrivate;
  }

  public void setIsPrivate(Boolean isPrivate) {
    this.isPrivate = isPrivate;
  }

  public Boolean getIsInvasive() {
    return isInvasive;
  }

  public void setIsInvasive(Boolean isInvasive) {
    this.isInvasive = isInvasive;
  }

  public Boolean getIsThreatened() {
    return isThreatened;
  }

  public void setIsThreatened(Boolean isThreatened) {
    this.isThreatened = isThreatened;
  }

  public Boolean getIsBIE() {
    return isBIE;
  }

  public void setIsBIE(Boolean isBIE) {
    this.isBIE = isBIE;
  }

  public Boolean getIsSDS() {
    return isSDS;
  }

  public void setIsSDS(Boolean isSDS) {
    this.isSDS = isSDS;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getOwnerName() {
    return ownerName;
  }

  public void setOwnerName(String ownerName) {
    this.ownerName = ownerName;
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

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
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

  // Helper method to handle WKT string to Geometry conversion if needed
  public void setWkt(String wktString) {
    if (wktString != null && !wktString.isEmpty()) {
      try {
        this.wkt = new WKTReader().read(wktString);
        this.wkt.setSRID(4326);
      } catch (Exception e) {
        // Log error or handle
        this.wkt = null;
      }
    }
  }

  public String getWkt() {
    return this.wkt != null ? this.wkt.toText() : null;
  }

  public SpeciesList() {}

  public SpeciesList(SpeciesList other) {
    this.id = other.id;
    this.version = other.version;
    this.dataResourceUid = other.dataResourceUid;
    this.title = other.title;
    this.description = other.description;
    this.listType = other.listType;
    this.licence = other.licence;
    this.originalFieldList =
        other.originalFieldList != null ? List.copyOf(other.originalFieldList) : null;
    this.fieldList = other.fieldList != null ? List.copyOf(other.fieldList) : null;
    this.facetList = other.facetList != null ? List.copyOf(other.facetList) : null;
    this.doi = other.doi;
    this.rowCount = other.rowCount;
    this.distinctMatchCount = other.distinctMatchCount;
    this.authority = other.authority;
    this.category = other.category;
    this.region = other.region;
    this.hasRegion = other.hasRegion;
    this.wkt = other.wkt;
    this.isVersioned = other.isVersioned;
    this.isAuthoritative = other.isAuthoritative;
    this.isPrivate = other.isPrivate;
    this.isInvasive = other.isInvasive;
    this.isThreatened = other.isThreatened;
    this.isBIE = other.isBIE;
    this.isSDS = other.isSDS;
    this.owner = other.owner;
    this.ownerName = other.ownerName;
    this.lastUpdatedBy = other.lastUpdatedBy;
    this.editors = other.editors != null ? List.copyOf(other.editors) : null;
    this.approvedViewers =
        other.approvedViewers != null ? List.copyOf(other.approvedViewers) : null;
    this.tags = other.tags != null ? List.copyOf(other.tags) : null;
    this.classification = other.classification;
    this.dateCreated = other.dateCreated != null ? new Date(other.dateCreated.getTime()) : null;
    this.metadataLastUpdated =
        other.metadataLastUpdated != null ? new Date(other.metadataLastUpdated.getTime()) : null;
    this.lastUpdated = other.lastUpdated != null ? new Date(other.lastUpdated.getTime()) : null;
    this.lastUploaded = other.lastUploaded != null ? new Date(other.lastUploaded.getTime()) : null;
  }
}
