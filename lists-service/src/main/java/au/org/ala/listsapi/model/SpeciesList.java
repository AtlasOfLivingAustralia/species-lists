package au.org.ala.listsapi.model;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@org.springframework.data.mongodb.core.mapping.Document(collection = "lists")
public class SpeciesList {

  @Id private String id;
  @Version private Integer version;
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

  Boolean isVersioned;
  Boolean isAuthoritative;
  Boolean isPrivate;
  Boolean isInvasive;
  Boolean isThreatened;
  Boolean isBIE;
  Boolean isSDS;

  private String owner; // who created the list
  private String lastUpdatedBy;
  private List<String> editors; // who can edit the list
  private List<String> approvedViewers; // who can view the list (when list is private)

  private Classification classification; // who created the list

  @CreatedDate public Date dateCreated;
  public Date lastUploaded;
  @LastModifiedDate public Date lastUpdated;
}
