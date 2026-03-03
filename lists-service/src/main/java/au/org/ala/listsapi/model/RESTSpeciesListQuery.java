package au.org.ala.listsapi.model;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "Unique identifier of the species list", hidden = true)
    private String id;
    @Schema(description = "Data resource UID associated with the list")
    private String dataResourceUid;
    @Schema(description = "Title of the species list", example = "status")
    private String title;
    @Schema(description = "Description of the species list")
    private String description;
    @Schema(description = "Type of list (see `/v2/constraints` for valid values)")
    private String listType;
    @Schema(description = "Licence applied to the list (see `/v2/constraints` for valid values)")
    private String licence;
    @Schema(description = "DOI associated with the list")
    private String doi;
    @Schema(description = "Category assigned to the list")
    private String category;
    @Schema(description = "Region covered by the list")
    private String region;
    @Schema(description = "Owner of the species list")
    private String owner;
    @Schema(description = "Whether the list is versioned", hidden = true)
    String isVersioned;
    @Schema(description = "Whether the list is authoritative", example = "false")
    String isAuthoritative;
    @Schema(description = "Whether the list is private", example = "false")
    String isPrivate;
    @Schema(description = "Whether the list is invasive", example = "false")
    String isInvasive;
    @Schema(description = "Whether the list is threatened", example = "true")
    String isThreatened;
    @Schema(description = "Whether the list appears on ALA species pages", example = "false")
    String isBIE;
    @Schema(description = "Whether the list is an SDS list", example = "false")
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
}
