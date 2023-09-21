package au.org.ala.listsapi.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "species-lists", createIndex = true)
@Setting(settingPath = "/elasticsearch/settings.json")
@Mapping(mappingPath = "/elasticsearch/mappings.json")
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class SpeciesListIndex {
  @Id private String id;
  private String dataResourceUid;
  private String speciesListName;
  private String listType;
  private String speciesListID;
  private String scientificName;
  private String vernacularName;
  private String taxonID;
  private String kingdom;
  private String phylum;
  private String classs;
  private String order;
  private String family;
  private String genus;
  private Map<String, String> properties;
  private Classification classification;
  private boolean isPrivate;
  private String owner;
  private List<String> editors;
}
