package au.org.ala.listsapi.model;

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

  public RESTSpeciesListQuery() {}

  public RESTSpeciesListQuery(
      String id,
      String dataResourceUid,
      String title,
      String description,
      String listType,
      String licence,
      String doi,
      String category,
      String region,
      String owner,
      String isVersioned,
      String isAuthoritative,
      String isPrivate,
      String isInvasive,
      String isThreatened,
      String isBIE,
      String isSDS) {
    this.id = id;
    this.dataResourceUid = dataResourceUid;
    this.title = title;
    this.description = description;
    this.listType = listType;
    this.licence = licence;
    this.doi = doi;
    this.category = category;
    this.region = region;
    this.owner = owner;
    this.isVersioned = isVersioned;
    this.isAuthoritative = isAuthoritative;
    this.isPrivate = isPrivate;
    this.isInvasive = isInvasive;
    this.isThreatened = isThreatened;
    this.isBIE = isBIE;
    this.isSDS = isSDS;
  }

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

  public RESTSpeciesListQuery copy() {
    return new RESTSpeciesListQuery(
        this.id,
        this.dataResourceUid,
        this.title,
        this.description,
        this.listType,
        this.licence,
        this.doi,
        this.category,
        this.region,
        this.owner,
        this.isVersioned,
        this.isAuthoritative,
        this.isPrivate,
        this.isInvasive,
        this.isThreatened,
        this.isBIE,
        this.isSDS);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDataResourceUid() {
    return dataResourceUid;
  }

  public void setDataResourceUid(String dataResourceUid) {
    this.dataResourceUid = dataResourceUid;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getListType() {
    return listType;
  }

  public void setListType(String listType) {
    this.listType = listType;
  }

  public String getLicence() {
    return licence;
  }

  public void setLicence(String licence) {
    this.licence = licence;
  }

  public String getDoi() {
    return doi;
  }

  public void setDoi(String doi) {
    this.doi = doi;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getIsVersioned() {
    return isVersioned;
  }

  public void setIsVersioned(String isVersioned) {
    this.isVersioned = isVersioned;
  }

  public String getIsAuthoritative() {
    return isAuthoritative;
  }

  public void setIsAuthoritative(String isAuthoritative) {
    this.isAuthoritative = isAuthoritative;
  }

  public String getIsPrivate() {
    return isPrivate;
  }

  public void setIsPrivate(String isPrivate) {
    this.isPrivate = isPrivate;
  }

  public String getIsInvasive() {
    return isInvasive;
  }

  public void setIsInvasive(String isInvasive) {
    this.isInvasive = isInvasive;
  }

  public String getIsThreatened() {
    return isThreatened;
  }

  public void setIsThreatened(String isThreatened) {
    this.isThreatened = isThreatened;
  }

  public String getIsBIE() {
    return isBIE;
  }

  public void setIsBIE(String isBIE) {
    this.isBIE = isBIE;
  }

  public String getIsSDS() {
    return isSDS;
  }

  public void setIsSDS(String isSDS) {
    this.isSDS = isSDS;
  }
}
