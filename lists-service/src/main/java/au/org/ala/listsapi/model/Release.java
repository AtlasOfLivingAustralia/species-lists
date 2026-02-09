package au.org.ala.listsapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@Entity
@Table(name = "release")
public class Release {
  @Id private String id;

  private Integer releasedVersion;

  @Column(name = "species_list_id")
  private String speciesListID;

  private String storedLocation;

  @JdbcTypeCode(SqlTypes.JSON)
  private SpeciesList metadata;

  @CreatedDate private LocalDateTime createdDate;

  @LastModifiedDate private LocalDateTime lastModifiedDate;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getReleasedVersion() {
    return releasedVersion;
  }

  public void setReleasedVersion(Integer releasedVersion) {
    this.releasedVersion = releasedVersion;
  }

  public String getSpeciesListID() {
    return speciesListID;
  }

  public void setSpeciesListID(String speciesListID) {
    this.speciesListID = speciesListID;
  }

  public String getStoredLocation() {
    return storedLocation;
  }

  public void setStoredLocation(String storedLocation) {
    this.storedLocation = storedLocation;
  }

  public SpeciesList getMetadata() {
    return metadata;
  }

  public void setMetadata(SpeciesList metadata) {
    this.metadata = metadata;
  }

  public LocalDateTime getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(LocalDateTime createdDate) {
    this.createdDate = createdDate;
  }

  public LocalDateTime getLastModifiedDate() {
    return lastModifiedDate;
  }

  public void setLastModifiedDate(LocalDateTime lastModifiedDate) {
    this.lastModifiedDate = lastModifiedDate;
  }
}
