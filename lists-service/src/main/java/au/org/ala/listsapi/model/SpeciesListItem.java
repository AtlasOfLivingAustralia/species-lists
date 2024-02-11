package au.org.ala.listsapi.model;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

@org.springframework.data.mongodb.core.mapping.Document(collection = "listItems")
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class SpeciesListItem {
  @Id private String id;
  @Version private Integer version;
  private String speciesListID;
  private String taxonID;
  private String scientificName;
  private String vernacularName;
  private String kingdom;
  private String phylum;
  private String classs;
  private String order;
  private String family;
  private String genus;
  private List<KeyValue> properties;
  private Classification classification;
  @CreatedDate public Date dateCreated;
  @LastModifiedDate public Date lastUpdated;
  private String lastUpdatedBy;

  public Map<String, String> toTaxonMap() {
    Map<String, String> taxon = new HashMap<>();
    taxon.put("taxonID", this.taxonID);
    taxon.put("scientificName", this.scientificName);
    taxon.put("vernacularName", this.vernacularName);
    taxon.put("kingdom", this.kingdom);
    taxon.put("phylum", this.phylum);
    taxon.put("class", this.classs);
    taxon.put("order", this.order);
    taxon.put("family", this.family);
    taxon.put("genus", this.genus);
    return taxon;
  }
}
