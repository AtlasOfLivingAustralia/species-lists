package au.org.ala.listsapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.CreatedDate;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@Entity
@Table(name = "ingest_progress")
public class IngestProgressItem {
  @Id private String id = UUID.randomUUID().toString();

  @Column(name = "species_list_id")
  private String speciesListID;

  private long rowCount;

  // Mapping mongoTotal to processed_count in DB as a temporary measure or keeping it
  @Column(name = "mongo_total")
  private long mongoTotal = 0;

  @Column(name = "elastic_total")
  private long elasticTotal = 0;

  private boolean completed = false;

  @CreatedDate
  @Column(name = "start_time")
  public Date started;

  public IngestProgressItem(String speciesListID, long rowCount) {
    this.speciesListID = speciesListID;
    this.rowCount = rowCount;
  }

  public IngestProgressItem(
      String speciesListID, long rowCount, long mongoTotal, long elasticTotal) {
    this.speciesListID = speciesListID;
    this.rowCount = rowCount;
    this.mongoTotal = mongoTotal;
    this.elasticTotal = elasticTotal;
  }
}
