package au.org.ala.listsapi.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@org.springframework.data.mongodb.core.mapping.Document(collection = "listItems")
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class SpeciesListItem {
    @JsonSerialize(using = ToStringSerializer.class)
    @Id
    private ObjectId id;
    @Version
    private Integer version;
    @Indexed
    private String speciesListID;
    private String taxonID;
    private String suppliedName;
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

    @CreatedDate
    public Date dateCreated;
    @LastModifiedDate
    public Date lastUpdated;
    private String lastUpdatedBy;

    public Map<String, String> toTaxonMap() {
        Map<String, String> taxon = new HashMap<>();
        taxon.put("taxonID", this.taxonID);
        taxon.put("scientificName", this.scientificName);
        taxon.put("vernacularName", this.vernacularName);
        taxon.put("kingdom", this.kingdom != null ? this.kingdom : this.properties != null ? this.properties.stream()
                .filter(kv -> "rawkingdom".equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst()
                .orElse(null) : null);
        taxon.put("phylum", this.phylum);
        taxon.put("class", this.classs);
        taxon.put("order", this.order);
        taxon.put("family", this.family != null ? this.family : this.properties != null ? this.properties.stream()
                .filter(kv -> "rawfamily".equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst()
                .orElse(null) : null);
        taxon.put("genus", this.genus);
        taxon.put("rank", this.properties != null ? this.properties.stream()
            .filter(kv -> "taxonRank".equals(kv.getKey()))
            .map(KeyValue::getValue)
            .findFirst()
            .orElse(null) : this.classification != null ? this.classification.getRank() : null);
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
}
