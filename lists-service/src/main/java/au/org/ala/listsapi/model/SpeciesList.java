package au.org.ala.listsapi.model;

import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@org.springframework.data.mongodb.core.mapping.Document(collection = "lists")
    public class SpeciesList {

    @Id private String id;
    @Version private Integer version;
    @Indexed private String dataResourceUid;
    private String title;
    private String description;
    private String listType;
    private String licence;
    private List<String> originalFieldList;
    private List<String> fieldList;
    private List<String> facetList;
    private String doi;
    private Integer rowCount;
    private Long distinctMatchCount;
    private String authority;
    private String category;
    private String region;
    private String wkt;

    Boolean isVersioned;
    Boolean isAuthoritative;
    Boolean isPrivate;
    Boolean isInvasive;
    Boolean isThreatened;
    Boolean isBIE;
    Boolean isSDS;

    private String owner; // user id of who created the list
    private String ownerName; // name of who created the list
    private String lastUpdatedBy;
    private List<String> editors; // who can edit the list
    private List<String> approvedViewers; // who can view the list (when list is private)
    private List<String> tags; // list of tags

    private Classification classification; // who created the list

    @CreatedDate public Date dateCreated;
    @LastModifiedDate public Date metadataLastUpdated;
    public Date lastUpdated;
    public Date lastUploaded;

    public SpeciesList(SpeciesList other) {
        this.id = other.id;
        this.version = other.version;
        this.dataResourceUid = other.dataResourceUid;
        this.title = other.title;
        this.description = other.description;
        this.listType = other.listType;
        this.licence = other.licence;
        this.originalFieldList = other.originalFieldList != null ? List.copyOf(other.originalFieldList) : null;
        this.fieldList = other.fieldList != null ? List.copyOf(other.fieldList) : null;
        this.facetList = other.facetList != null ? List.copyOf(other.facetList) : null;
        this.doi = other.doi;
        this.rowCount = other.rowCount;
        this.distinctMatchCount = other.distinctMatchCount;
        this.authority = other.authority;
        this.category = other.category;
        this.region = other.region;
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
        this.approvedViewers = other.approvedViewers != null ? List.copyOf(other.approvedViewers) : null;
        this.tags = other.tags != null ? List.copyOf(other.tags) : null;
        this.classification = other.classification;
        this.dateCreated = other.dateCreated != null ? new Date(other.dateCreated.getTime()) : null;
        this.metadataLastUpdated = other.metadataLastUpdated != null ? new Date(other.metadataLastUpdated.getTime()) : null;
        this.lastUpdated = other.lastUpdated != null ? new Date(other.lastUpdated.getTime()) : null;
        this.lastUploaded = other.lastUploaded != null ? new Date(other.lastUploaded.getTime()) : null;
    }

    public String getId() { return id; }
    public Boolean getIsPrivate() { return isPrivate; }
    public List<String> getFieldList() { return fieldList; }
    public Boolean getIsAuthoritative() { return isAuthoritative; }
    public Boolean getIsThreatened() { return isThreatened; }
    public Boolean getIsInvasive() { return isInvasive; }
    public Boolean getIsBIE() { return isBIE; }
    public Boolean getIsSDS() { return isSDS; }
    public String getDataResourceUid() { return dataResourceUid; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getListType() { return listType; }
    public String getLicence() { return licence; }
    public List<String> getFacetList() { return facetList; }
    public String getRegion() { return region; }
    public String getWkt() { return wkt; }
    public String getOwner() { return owner; }
    public List<String> getEditors() { return editors; }
    public List<String> getTags() { return tags; }
    public Date getDateCreated() { return dateCreated; }
    public Date getLastUpdated() { return lastUpdated; }
    public String getLastUpdatedBy() { return lastUpdatedBy; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Long getDistinctMatchCount() { return distinctMatchCount; }
    public void setDistinctMatchCount(Long distinctMatchCount) { this.distinctMatchCount = distinctMatchCount; }
}
