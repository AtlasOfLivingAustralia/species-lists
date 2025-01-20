package au.org.ala.listsapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class IngestProgressItem {
    private long mongoProgress;
    private long elasticProgress;
    private long createdTimestamp;
}
