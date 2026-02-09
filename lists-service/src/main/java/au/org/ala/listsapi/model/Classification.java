package au.org.ala.listsapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Classification {
  Boolean success;

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

  @JsonProperty("class")
  public String getClasss() {
    return this.classs;
  }

  public void setClasss(String classs) {
    this.classs = classs;
  }

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  public String getScientificNameAuthorship() {
    return scientificNameAuthorship;
  }

  public void setScientificNameAuthorship(String scientificNameAuthorship) {
    this.scientificNameAuthorship = scientificNameAuthorship;
  }

  public String getTaxonConceptID() {
    return taxonConceptID;
  }

  public void setTaxonConceptID(String taxonConceptID) {
    this.taxonConceptID = taxonConceptID;
  }

  public String getRank() {
    return rank;
  }

  public void setRank(String rank) {
    this.rank = rank;
  }

  public Integer getRankID() {
    return rankID;
  }

  public void setRankID(Integer rankID) {
    this.rankID = rankID;
  }

  public String getMatchType() {
    return matchType;
  }

  public void setMatchType(String matchType) {
    this.matchType = matchType;
  }

  public String getNameType() {
    return nameType;
  }

  public void setNameType(String nameType) {
    this.nameType = nameType;
  }

  public String getKingdom() {
    return kingdom;
  }

  public void setKingdom(String kingdom) {
    this.kingdom = kingdom;
  }

  public String getKingdomID() {
    return kingdomID;
  }

  public void setKingdomID(String kingdomID) {
    this.kingdomID = kingdomID;
  }

  public String getPhylum() {
    return phylum;
  }

  public void setPhylum(String phylum) {
    this.phylum = phylum;
  }

  public String getPhylumID() {
    return phylumID;
  }

  public void setPhylumID(String phylumID) {
    this.phylumID = phylumID;
  }

  public String getClassID() {
    return classID;
  }

  public void setClassID(String classID) {
    this.classID = classID;
  }

  public String getOrder() {
    return order;
  }

  public void setOrder(String order) {
    this.order = order;
  }

  public String getOrderID() {
    return orderID;
  }

  public void setOrderID(String orderID) {
    this.orderID = orderID;
  }

  public String getFamily() {
    return family;
  }

  public void setFamily(String family) {
    this.family = family;
  }

  public String getFamilyID() {
    return familyID;
  }

  public void setFamilyID(String familyID) {
    this.familyID = familyID;
  }

  public String getGenus() {
    return genus;
  }

  public void setGenus(String genus) {
    this.genus = genus;
  }

  public String getGenusID() {
    return genusID;
  }

  public void setGenusID(String genusID) {
    this.genusID = genusID;
  }

  public String getSpecies() {
    return species;
  }

  public void setSpecies(String species) {
    this.species = species;
  }

  public String getSpeciesID() {
    return speciesID;
  }

  public void setSpeciesID(String speciesID) {
    this.speciesID = speciesID;
  }

  public String getVernacularName() {
    return vernacularName;
  }

  public void setVernacularName(String vernacularName) {
    this.vernacularName = vernacularName;
  }

  public List<String> getSpeciesGroup() {
    return speciesGroup;
  }

  public void setSpeciesGroup(List<String> speciesGroup) {
    this.speciesGroup = speciesGroup;
  }

  public List<String> getSpeciesSubgroup() {
    return speciesSubgroup;
  }

  public void setSpeciesSubgroup(List<String> speciesSubgroup) {
    this.speciesSubgroup = speciesSubgroup;
  }

  public List<String> getIssues() {
    return issues;
  }

  public void setIssues(List<String> issues) {
    this.issues = issues;
  }

  public Integer getLft() {
    return lft;
  }

  public void setLft(Integer lft) {
    this.lft = lft;
  }

  public Integer getRgt() {
    return rgt;
  }

  public void setRgt(Integer rgt) {
    this.rgt = rgt;
  }
}
