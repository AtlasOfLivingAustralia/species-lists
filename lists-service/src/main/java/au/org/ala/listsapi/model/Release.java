package au.org.ala.listsapi.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@org.springframework.data.mongodb.core.mapping.Document(collection = "releases")
public class Release {
  @Id private String id;
  private Integer releasedVersion;
  private String speciesListID;
  private String storedLocation;
  private SpeciesList metadata;
  @CreatedDate public LocalDateTime createdDate;
  @LastModifiedDate public LocalDateTime lastModifiedDate;
}
