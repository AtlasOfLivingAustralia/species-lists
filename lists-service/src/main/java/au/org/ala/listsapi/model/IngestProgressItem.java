package au.org.ala.listsapi.model;

import java.util.Date;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;

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
@org.springframework.data.mongodb.core.mapping.Document(collection = "ingestionProgress")
public class IngestProgressItem {
    @Id private String id;
    @org.springframework.data.mongodb.core.index.Indexed(unique = true)
    private String speciesListID;
    private long rowCount;
    private long mongoTotal = 0;
    private long elasticTotal = 0;
    private boolean completed = false;

    @CreatedDate public Date started;

    public IngestProgressItem(String speciesListID, long rowCount) {
        this.speciesListID = speciesListID;
        this.rowCount = rowCount;
    }

    public IngestProgressItem(String speciesListID, long rowCount, long mongoTotal, long elasticTotal) {
        this.speciesListID = speciesListID;
        this.rowCount = rowCount;
        this.mongoTotal = mongoTotal;
        this.elasticTotal = elasticTotal;
    }
}
