package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.InputSpeciesList;
import au.org.ala.listsapi.model.Release;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.ReleaseService;
import au.org.ala.listsapi.service.TaxonService;
import au.org.ala.listsapi.service.UploadService;
import au.org.ala.ws.security.profile.AlaUserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.security.Principal;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Ingress REST API */
@CrossOrigin(origins = "*", maxAge = 3600)
@SecurityScheme(
    name = "JWT",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
@org.springframework.web.bind.annotation.RestController
public class IngressController {

  private static final Logger logger = LoggerFactory.getLogger(IngressController.class);
  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
  @Autowired protected TaxonService taxonService;
  @Autowired protected ReleaseService releaseService;
  @Autowired protected UploadService uploadService;
  @Autowired protected AuthUtils authUtils;

  @Value("${temp.dir:/tmp}")
  private String tempDir;

  @SecurityRequirement(name = "JWT")
  @Operation(tags = "Ingress", summary = "Release a version of a species list")
  @GetMapping("/release/{speciesListID}")
  public ResponseEntity<Object> release(
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(speciesListID, principal);
      if (errorResponse != null) return errorResponse;
      Release release = releaseService.release(speciesListID);
      return new ResponseEntity<>(release, HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Error while releasing the file: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(tags = "Ingress", summary = "Delete a species list")
  @DeleteMapping("/delete/{speciesListID}")
  public ResponseEntity<Object> delete(
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(speciesListID, principal);
      if (errorResponse != null) return errorResponse;

      boolean success = uploadService.deleteList(speciesListID, (AlaUserProfile) principal);
      if (success) return new ResponseEntity<>(HttpStatus.OK);
      else return ResponseEntity.badRequest().body("Unable to delete species list");
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body("Error while deleting species list: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(tags = "Ingress", summary = "Rematch the taxonomy for a species list")
  @GetMapping("/rematch/{speciesListID}")
  public ResponseEntity<Object> rematch(
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(speciesListID, principal);
      if (errorResponse != null) {
        return errorResponse;
      }
      taxonService.taxonMatchDataset(speciesListID);
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return ResponseEntity.badRequest()
          .body("Error while rematching the taxonomy for a list: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(tags = "Ingress", summary = "Rematch the taxonomy for all species lists")
  @GetMapping("/rematch")
  public ResponseEntity<Object> rematch(@AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(principal);
      if (errorResponse != null) return errorResponse;
      taxonService.taxonMatchDatasets();
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return ResponseEntity.badRequest()
          .body("Error while rematch the taxonomy for all lists: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Reindex a species lists", tags = "Ingress")
  @GetMapping("/reindex/{speciesListID}")
  public ResponseEntity<Object> reindex(
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(speciesListID, principal);
      if (errorResponse != null) return errorResponse;

      taxonService.taxonMatchDataset(speciesListID);
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      logger.error("Error while reindexing dataset " + e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error while reindexing dataset: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Tag(name = "Ingress", description = "Services for ingesting species lists")
  @Operation(summary = "Reindex all species lists", tags = "Ingress")
  @GetMapping("/reindex")
  public ResponseEntity<Object> reindex(@AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(principal);
      if (errorResponse != null) return errorResponse;
      taxonService.reindex();
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      logger.error("Error while reindexing all datasets: " + e.getMessage(), e);
      return ResponseEntity.badRequest()
          .body("Error while reindexing all datasets: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Upload a CSV species list", tags = "Ingress")
  @PostMapping("/upload")
  public ResponseEntity<Object> handleFileUpload(@RequestParam("file") MultipartFile file) {

    try {
      logger.info("Upload to temporary area started...");
      File tempFile = uploadService.getFileTemp(file);
      file.transferTo(tempFile);
      IngestJob ingestJob = uploadService.ingest(tempFile, true, true);
      return new ResponseEntity<>(ingestJob, HttpStatus.OK);
    } catch (Exception e) {
      logger.error("Error while uploading the file: " + e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error while uploading the file: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Ingest a species list", tags = "Ingress")
  @PostMapping("/ingest")
  public ResponseEntity<Object> ingest(
      @RequestParam("file") String fileName,
      InputSpeciesList speciesList,
      @AuthenticationPrincipal Principal principal) {
    try {

      // check user logged in
      AlaUserProfile alaUserProfile = (AlaUserProfile) principal;
      if (alaUserProfile == null) {
        return ResponseEntity.badRequest().body("User not found");
      }

      logger.info("Ingestion started...");
      File tempFile = new File(tempDir + "/" + fileName);
      if (!tempFile.exists()) {
        return ResponseEntity.badRequest().body("File not uploaded yet");
      }

      SpeciesList updatedSpeciesList =
          uploadService.ingest(alaUserProfile.getUserId(), speciesList, tempFile, false);
      logger.info("Ingestion complete..." + updatedSpeciesList.toString());
      return new ResponseEntity<>(updatedSpeciesList, HttpStatus.OK);
    } catch (Exception e) {
      logger.error("Error while ingesting the file: " + e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error while uploading the file: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Ingest a species list that has been uploaded before", tags = "Ingress")
  @PostMapping("/ingest/{speciesListID}")
  public ResponseEntity<Object> ingest(
      @RequestParam("file") String fileName,
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal) {
    try {

      ResponseEntity<Object> errorResponse = checkAuthorized(speciesListID, principal);
      if (errorResponse != null) return errorResponse;

      logger.info("Re-Ingestion started...");
      File tempFile = new File(tempDir + "/" + fileName);
      if (!tempFile.exists()) {
        return ResponseEntity.badRequest().body("File not uploaded yet");
      }
      SpeciesList speciesList = uploadService.reload(speciesListID, tempFile, false);
      if (speciesList != null) {
        // release current version
        releaseService.release(speciesListID);
        return new ResponseEntity<>(speciesList, HttpStatus.OK);
      }
      return ResponseEntity.badRequest().body("Error while uploading the file: ");
    } catch (Exception e) {
      logger.error("Error while ingesting the file: " + e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error while uploading the file: " + e.getMessage());
    }
  }

  @Nullable
  private ResponseEntity<Object> checkAuthorized(String speciesListID, Principal principal) {
    // check user logged in
    AlaUserProfile alaUserProfile = (AlaUserProfile) principal;
    if (alaUserProfile == null) {
      return ResponseEntity.badRequest().body("User not found");
    }

    // check it exists
    Optional<SpeciesList> speciesListOptional = speciesListMongoRepository.findById(speciesListID);
    if (!speciesListOptional.isPresent()) {
      return ResponseEntity.badRequest().body("Species list not found");
    }

    // check authorised
    if (!authUtils.isAuthorized(speciesListOptional.get(), principal)) {
      return ResponseEntity.badRequest().body("User not authorized");
    }
    return null;
  }

  @Nullable
  private ResponseEntity<Object> checkAuthorized(Principal principal) {
    // check user logged in
    AlaUserProfile alaUserProfile = (AlaUserProfile) principal;
    if (alaUserProfile == null) {
      return ResponseEntity.badRequest().body("User not found");
    }

    // check authorised
    if (!authUtils.isAuthorized(principal)) {
      return ResponseEntity.badRequest().body("User not authorized");
    }
    return null;
  }
}
