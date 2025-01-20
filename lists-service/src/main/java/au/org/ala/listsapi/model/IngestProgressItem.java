package au.org.ala.listsapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
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
@org.springframework.data.mongodb.core.mapping.Document(collection = "ingestionProgress")
public class IngestProgressItem {
    @Id private String id;
    private String speciesListId;
    private long mongoProgress;
    private long elasticProgress;

    @CreatedDate public Date started;

    public IngestProgressItem(String speciesListId, long mongoProgress, long elasticProgress) {
        this.speciesListId = speciesListId;
        this.mongoProgress = mongoProgress;
        this.elasticProgress = elasticProgress;
    }
}
