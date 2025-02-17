package au.org.ala.listsapi.model;

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
public class RESTSpeciesListQuery {

  private String id;
  private String dataResourceUid;
  private String title;
  private String description;
  private String listType;
  private String licence;
  private String doi;
  private String category;
  private String region;
  private String owner;
  String isVersioned;
  String isAuthoritative;
  String isPrivate;
  String isInvasive;
  String isThreatened;
  String isBIE;
  String isSDS;

  public boolean isEmpty() {
    if ((id != null && !id.isEmpty())
        || (dataResourceUid != null && !dataResourceUid.isEmpty())
        || (title != null && !title.isEmpty())
        || (description != null && !description.isEmpty())
        || (listType != null && !listType.isEmpty())
        || (licence != null && !licence.isEmpty())
        || (doi != null && !doi.isEmpty())
        || (category != null && !category.isEmpty())
        || (region != null && !region.isEmpty())
        || (owner != null && !owner.isEmpty())
        || (isVersioned != null && !isVersioned.isEmpty())
        || (isAuthoritative != null && !isAuthoritative.isEmpty())
        || (isPrivate != null && !isPrivate.isEmpty())
        || (isInvasive != null && !isInvasive.isEmpty())
        || (isThreatened != null && !isThreatened.isEmpty())
        || (isBIE != null && !isBIE.isEmpty())
        || (isSDS != null && !isSDS.isEmpty())) {
      return false;
    }
    return true;
  }

  public SpeciesList convertTo() {
    SpeciesList s = new SpeciesList();
    s.setIsPrivate(parseBoolean(removeQueryExpr(this.isPrivate)));
    s.setIsAuthoritative(parseBoolean(removeQueryExpr(this.isAuthoritative)));
    s.setIsInvasive(parseBoolean(removeQueryExpr(this.isInvasive)));
    s.setIsThreatened(parseBoolean(removeQueryExpr(this.isThreatened)));
    s.setIsBIE(parseBoolean(removeQueryExpr(this.isBIE)));
    s.setIsSDS(parseBoolean(removeQueryExpr(this.isSDS)));
    s.setOwner(this.owner);
    s.setCategory(this.category);
    s.setRegion(this.region);
    s.setLicence(this.licence);
    s.setDoi(this.doi);
    s.setDescription(this.description);
    s.setTitle(this.title);
    s.setDataResourceUid(this.dataResourceUid);
    s.setListType(this.listType);
    s.setId(this.id);
    return s;
  }

  public static Boolean parseBoolean(String s) {
    if (s == null) {
      return null;
    }
    return Boolean.parseBoolean(s);
  }

  public static String removeQueryExpr(String s) {
    if (s != null && s.startsWith("eq:")) {
      return s.substring(3);
    }
    return s;
  }
}
