package au.org.ala.listsapi.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@Entity
@Table(name = "migration_progress")
public class MigrateProgressItem {
  @Id private String id = "_";

  @JdbcTypeCode(SqlTypes.JSON)
  private SpeciesList currentSpeciesList;

  private long completed = 0;
  private long total;

  @CreatedDate public Date started;

  public MigrateProgressItem(long total) {
    this.total = total;
  }
}
