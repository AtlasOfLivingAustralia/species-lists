package au.org.ala.listsapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;

import java.util.Date;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@org.springframework.data.mongodb.core.mapping.Document(collection = "migrationProgress")
public class MigrateProgressItem {
    @Id private String id = "_";
    private SpeciesList currentSpeciesList;
    private long completed = 0;
    private long total;

    @CreatedDate public Date started;

    public MigrateProgressItem(long total) {
        this.total = total;
    }
}
