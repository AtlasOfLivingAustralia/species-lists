package au.org.ala.listsapi.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Jacksonized
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestJob {
    List<String> fieldList;
    List<String> originalFieldNames;
    List<String> facetList;
    @Builder.Default
    int rowCount = 0;
    @Builder.Default
    long distinctMatchCount = 0;
    String localFile;
    List<String> validationErrors;
}
