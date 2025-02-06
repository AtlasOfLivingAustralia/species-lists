package au.org.ala.listsapi.model;

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
public class InputSpeciesList {

  private String id;
  private Integer version;
  private String dataResourceUid;
  private String title;
  private String description;
  private String listType;
  private String licence;
  private List<String> originalFieldList;
  private List<String> fieldList;
  private List<String> facetList;
  private String doi;
  private Integer rowCount;
  private String authority;
  private String category;
  private String region;
  private String wkt;
  private List<String> tags;

  String isAuthoritative;
  String isPrivate;
  String isInvasive;
  String isThreatened;
  String isBIE;
  String isSDS;

  private String owner; // who created the list
  private String lastUpdatedBy;
  private List<String> editors; // who can edit the list
  private List<String> approvedViewers; // who can view the list (when list is private)

  private Classification classification;

  @CreatedDate public Date dateCreated;
  @LastModifiedDate public Date metadataLastUpdated;
  public Date lastUpdated;
  public Date lastUploaded;
}
