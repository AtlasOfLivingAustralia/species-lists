package au.org.ala.listsapi.model;

import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

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
@Schema(description = "Input specification for species list")
public class InputSpeciesList {

    @Schema(description = "Unique identifier (auto-generated)", accessMode = Schema.AccessMode.READ_ONLY)
    private String id;

    @Schema(description = "Version of the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer version;

    @Schema(description = "Data resource UID associated with the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private String dataResourceUid;

    @Schema(description = "Title of the species list", required = true)
    private String title;

    @Schema(description = "Description of the species list", required = true)
    private String description;

    @Schema(description = "Type of the species list", required = true)
    private String listType;

    @Schema(description = "Licence under which the species list is shared", required = true)
    private String licence;

    @Schema(description = "Original field list provided for the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private List<String> originalFieldList;

    @Schema(description = "Field list used in the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private List<String> fieldList;

    @Schema(description = "Facet list for the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private List<String> facetList;

    @Schema(description = "Digital Object Identifier (DOI) for the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private String doi;

    @Schema(description = "Number of rows in the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer rowCount;

    @Schema(description = "Authority responsible for the species list")
    private String authority;

    @Schema(description = "Category of the species list")
    private String category;

    @Schema(description = "Region associated with the species list")
    private String region;

    @Schema(description = "Well-Known Text (WKT) representation of the species list's spatial data")
    private String wkt;

    @Schema(description = "Tags associated with the species list")
    private List<String> tags;

    @Schema(description = "Indicates if the species list is authoritative", example = "true|false")
    String isAuthoritative;

    @Schema(description = "Indicates if the species list is private", example = "true|false")
    String isPrivate;

    @Schema(description = "Indicates if the species list is invasive", example = "true|false")
    String isInvasive;

    @Schema(description = "Indicates if the species list is threatened", example = "true|false")
    String isThreatened;

    @Schema(description = "Indicates if the species list is included in BIE", example = "true|false")
    String isBIE;

    @Schema(description = "Indicates if the species list is part of SDS", example = "true|false")
    String isSDS;

    @Schema(description = "Owner of the species list, who created the list", accessMode = Schema.AccessMode.READ_ONLY)
    private String owner;

    @Schema(description = "User who last updated the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private String lastUpdatedBy;

    @Schema(description = "List of users who can edit the species list")
    private List<String> editors;

    @Schema(description = "List of users who can view the species list when it is private")
    private List<String> approvedViewers;

    @Schema(description = "Classification details of the species list", accessMode = Schema.AccessMode.READ_ONLY)
    private Classification classification;

    @Schema(description = "Date when the species list was created", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate
    public Date dateCreated;

    @Schema(description = "Date when the metadata of the species list was last updated", accessMode = Schema.AccessMode.READ_ONLY)
    @LastModifiedDate
    public Date metadataLastUpdated;

    @Schema(description = "Date when the species list was last updated", accessMode = Schema.AccessMode.READ_ONLY)
    public Date lastUpdated;

    @Schema(description = "Date when the species list was last uploaded", accessMode = Schema.AccessMode.READ_ONLY)
    public Date lastUploaded;
}
