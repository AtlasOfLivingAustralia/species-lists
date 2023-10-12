package au.org.ala.listsapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Classification {
  Boolean success;

  @Field(
      name = "scientificName",
      type = FieldType.Text,
      copyTo = "all",
      analyzer = "custom_standard_analyzer")
  String scientificName;

  String scientificNameAuthorship;
  String taxonConceptID;
  String rank;
  Integer rankID;
  String matchType;
  String nameType;
  String kingdom;
  String kingdomID;
  String phylum;
  String phylumID;
  String classs;
  String classID;
  String order;
  String orderID;
  String family;
  String familyID;
  String genus;
  String genusID;
  String species;
  String speciesID;
  String vernacularName;
  List<String> speciesGroup;
  List<String> speciesSubgroup;
  List<String> issues;
  Integer lft;
  Integer rgt;
}
