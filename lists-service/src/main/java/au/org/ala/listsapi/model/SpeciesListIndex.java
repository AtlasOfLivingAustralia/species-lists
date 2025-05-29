package au.org.ala.listsapi.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * SpeciesListIndex is a model/bean that represents a single elastic search document and
 * each entry corresponds to a denormalised taxon row entry for a species list. Individual 
 * lists are represented by aggregating the entries for each list's taxa in the index.
 */
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
  private List<KeyValue> properties;
  private Classification classification;
  private boolean isPrivate;
  private boolean isAuthoritative;
  private boolean isBIE;
  private boolean isSDS;
  private boolean hasRegion;
  private String owner;
  private List<String> editors;
  private List<String> tags;
  public String dateCreated;
  public String lastUpdated;
  private String lastUpdatedBy;
}
