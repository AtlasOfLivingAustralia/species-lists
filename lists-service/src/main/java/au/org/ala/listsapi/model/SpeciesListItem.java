package au.org.ala.listsapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "species_list_item")
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class SpeciesListItem implements Persistable<String> {
  @JsonSerialize(using = ToStringSerializer.class)
  @Id
  private String id;

  @Version private Integer version;

  @Transient private boolean isNew = true;

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Column(name = "species_list_id")
  private String speciesListID;

  @Column(name = "taxon_id")
  private String taxonID;

  private String suppliedName;
  private String scientificName;
  private String vernacularName;
  private String kingdom;
  private String phylum;

  @Column(name = "classs")
  private String classs;

  @Column(name = "\"order\"")
  private String order;

  private String family;
  private String genus;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<KeyValue> properties;

  @JdbcTypeCode(SqlTypes.JSON)
  private Classification classification;

  @CreatedDate public Date dateCreated;

  @LastModifiedDate public Date lastUpdated;

  private String lastUpdatedBy;

  // Computed search vector for read-only access from Java if needed,
  // though usually handled by DB
  @Column(name = "search_vector", insertable = false, updatable = false)
  private String searchVector;

  public Map<String, String> toTaxonMap() {
    Map<String, String> taxon = new HashMap<>();
    taxon.put("taxonID", this.taxonID);
    taxon.put("scientificName", this.scientificName);
    taxon.put("vernacularName", this.vernacularName);
    taxon.put(
        "kingdom",
        this.kingdom != null
            ? this.kingdom
            : this.properties != null
                ? this.properties.stream()
                    .filter(kv -> "rawkingdom".equals(kv.getKey()))
                    .map(KeyValue::getValue)
                    .findFirst()
                    .orElse(null)
                : null);
    taxon.put("phylum", this.phylum);
    taxon.put("class", this.classs);
    taxon.put("order", this.order);
    taxon.put(
        "family",
        this.family != null
            ? this.family
            : this.properties != null
                ? this.properties.stream()
                    .filter(kv -> "rawfamily".equals(kv.getKey()))
                    .map(KeyValue::getValue)
                    .findFirst()
                    .orElse(null)
                : null);
    taxon.put("genus", this.genus);
    taxon.put(
        "rank",
        this.properties != null
            ? this.properties.stream()
                .filter(kv -> "taxonRank".equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst()
                .orElse(null)
            : this.classification != null ? this.classification.getRank() : null);
    return taxon;
  }

  public List<String> toTaxonList() {
    List<String> taxon = new ArrayList<>();

    if (this.taxonID != null) {
      taxon.add(this.taxonID);
    } else if (this.scientificName != null) {
      taxon.add(this.scientificName);
    } else {
      taxon.add(null);
    }

    return taxon;
  }

  public Object getPropFromKey(String key) {
    switch (key) {
      case "id":
        return this.id;
      case "version":
        return this.version;
      case "speciesListID":
        return this.speciesListID;
      case "taxonID":
        return this.taxonID;
      case "suppliedName":
        return this.suppliedName;
      case "scientificName":
        return this.scientificName;
      case "vernacularName":
        return this.vernacularName;
      case "kingdom":
        return this.kingdom;
      case "phylum":
        return this.phylum;
      case "classs":
        return this.classs;
      case "order":
        return this.order;
      case "family":
        return this.family;
      case "genus":
        return this.genus;
      case "properties":
        return this.properties;
      case "classification":
        return this.classification;
      case "dateCreated":
        return this.dateCreated;
      case "lastUpdated":
        return this.lastUpdated;
      case "lastUpdatedBy":
        return this.lastUpdatedBy;
    }

    return null;
  }

  @JsonProperty("class")
  public String getClasss() {
    return this.classs;
  }

  public void setClasss(String classs) {
    this.classs = classs;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getSpeciesListID() {
    return speciesListID;
  }

  public void setSpeciesListID(String speciesListID) {
    this.speciesListID = speciesListID;
  }

  public String getTaxonID() {
    return taxonID;
  }

  public void setTaxonID(String taxonID) {
    this.taxonID = taxonID;
  }

  public String getSuppliedName() {
    return suppliedName;
  }

  public void setSuppliedName(String suppliedName) {
    this.suppliedName = suppliedName;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  public String getVernacularName() {
    return vernacularName;
  }

  public void setVernacularName(String vernacularName) {
    this.vernacularName = vernacularName;
  }

  public String getKingdom() {
    return kingdom;
  }

  public void setKingdom(String kingdom) {
    this.kingdom = kingdom;
  }

  public String getPhylum() {
    return phylum;
  }

  public void setPhylum(String phylum) {
    this.phylum = phylum;
  }

  public String getOrder() {
    return order;
  }

  public void setOrder(String order) {
    this.order = order;
  }

  public String getFamily() {
    return family;
  }

  public void setFamily(String family) {
    this.family = family;
  }

  public String getGenus() {
    return genus;
  }

  public void setGenus(String genus) {
    this.genus = genus;
  }

  public List<KeyValue> getProperties() {
    return properties;
  }

  public void setProperties(List<KeyValue> properties) {
    this.properties = properties;
  }

  public Classification getClassification() {
    return classification;
  }

  public void setClassification(Classification classification) {
    this.classification = classification;
  }

  public Date getDateCreated() {
    return dateCreated;
  }

  public void setDateCreated(Date dateCreated) {
    this.dateCreated = dateCreated;
  }

  public Date getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public String getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  public void setLastUpdatedBy(String lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }
}
