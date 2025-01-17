package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.*;
import au.org.ala.ws.security.profile.AlaUserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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

  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;

  @Autowired protected TaxonService taxonService;
  @Autowired protected ReleaseService releaseService;
  @Autowired protected UploadService uploadService;
  @Autowired protected MigrateService migrateService;
  @Autowired protected ValidationService validationService;

  @Autowired protected AuthUtils authUtils;

  @Value("${temp.dir:/tmp}")
  private String tempDir;

  private CompletableFuture<Void> asyncTask;
  private String taskName;

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
      return ResponseEntity.ok(release);
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
  @Operation(
      tags = "Ingress",
      description = "Rematch the taxonomy for a species list. This is a long running process.",
      summary = "Rematch the taxonomy for a species list")
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
      taxonService.reindex(speciesListID);
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return ResponseEntity.badRequest()
          .body("Error while rematching the taxonomy for a list: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(
      tags = "Ingress",
      description = "Rematch the taxonomy for all species lists. This is a long running process.",
      summary = "Rematch the taxonomy for all species lists")
  @GetMapping("/admin/rematch")
  public ResponseEntity<Object> rematch(@AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(principal);
      if (errorResponse != null) return errorResponse;
      return startAsyncTaskIfNotBusy("REMATCH", () -> taxonService.taxonMatchDatasets());
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return ResponseEntity.badRequest()
          .body("Error while rematch the taxonomy for all lists: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(
      summary = "Reindex a species list",
      description = "Reindex a species list into the ElasticSearch index",
      tags = "Ingress")
  @GetMapping("/reindex/{speciesListID}")
  public ResponseEntity<Object> reindex(
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(speciesListID, principal);
      if (errorResponse != null) return errorResponse;

      taxonService.reindex(speciesListID);
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      logger.error("Error while reindexing dataset " + e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error while reindexing dataset: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Tag(name = "Ingress", description = "Services for ingesting species lists")
  @Operation(
      summary = "Reindex all species lists",
      description = "Reindex all species lists into the ElasticSearch index.",
      tags = "Ingress")
  @GetMapping("/admin/reindex")
  public ResponseEntity<Object> reindex(@AuthenticationPrincipal Principal principal) {
    try {
      ResponseEntity<Object> errorResponse = checkAuthorized(principal);
      if (errorResponse != null) return errorResponse;
      // start async task
      return startAsyncTaskIfNotBusy("REINDEX", () -> taxonService.reindex());

    } catch (Exception e) {
      logger.error("Error while reindexing all datasets: " + e.getMessage(), e);
      return ResponseEntity.badRequest()
          .body("Error while reindexing all datasets: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(
      summary = "Upload a CSV species list",
      tags = "Ingress",
      description =
          "Upload a CSV species list. This is step 1 of a 2 step process. "
              + "The file is uploaded to a temporary area and then ingested. "
              + "The second step is to ingest the species list.")
  @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully uploaded",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = IngestJob.class))
            }),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request",
            content = {@Content(mediaType = "text/plain")})
      })
  public ResponseEntity<Object> handleFileUpload(@RequestPart("file") MultipartFile file) {

    try {
      logger.info("Upload to temporary area started...");
      File tempFile = uploadService.getFileTemp(file);
      file.transferTo(tempFile);
      IngestJob ingestJob = uploadService.ingest(tempFile, true, true);
      return ResponseEntity.ok(ingestJob);
    } catch (Exception e) {
      logger.error("Error while uploading the file: " + e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error while uploading the file: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(
      summary = "Ingest a species list",
      tags = "Ingress",
      description =
          "Ingest a species list. This is step 2 of a 2 step process. "
              + "The file is uploaded to a temporary area and then ingested. "
              + "The first step is to upload the species list.")
  @PostMapping("/ingest")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully ingested",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = SpeciesList.class))
            }),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request",
            content = {@Content(mediaType = "text/plain")})
      })
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

      // check that the supplied list type, region and license is valid
      if (!validationService.isListValid(speciesList)) {
        return ResponseEntity.badRequest().body("Supplied list contains invalid properties for a controlled value (list type, license, region)");
      }

      logger.info("Ingestion started...");
      File tempFile = new File(tempDir + "/" + fileName);
      if (!tempFile.exists()) {
        return ResponseEntity.badRequest().body("File not uploaded yet");
      }



      SpeciesList updatedSpeciesList =
          uploadService.ingest(alaUserProfile.getGivenName() + " " + alaUserProfile.getFamilyName(), speciesList, tempFile, false);
      logger.info("Ingestion complete..." + updatedSpeciesList.toString());
      return ResponseEntity.ok(updatedSpeciesList);
    } catch (Exception e) {
      logger.error("Error while ingesting the file: " + e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error while uploading the file: " + e.getMessage());
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(
      summary = "Ingest a species list that has been uploaded before",
      description =
          "Ingest a species list. This is step 2 of a 2 step process. "
              + "The file is uploaded to a temporary area and then ingested. "
              + "The first step is to upload the species list.",
      tags = "Ingress")
  @PostMapping("/ingest/{speciesListID}")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully ingested",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = SpeciesList.class))
            }),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request",
            content = {@Content(mediaType = "text/plain")})
      })
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
    if (speciesListOptional.isEmpty()) {
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

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Migration status", tags = "Migrate")
  @GetMapping("/migrate/status")
  public ResponseEntity<Object> migrateStatus() {
    if (asyncTask != null && !asyncTask.isDone()) {
      return new ResponseEntity<>(new AsyncTaskStatus(taskName, true), HttpStatus.OK);
    } else {
      return new ResponseEntity<>(new AsyncTaskStatus(taskName, false), HttpStatus.OK);
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Migrate all species lists", tags = "Migrate")
  @GetMapping("/admin/migrate/all")
  public ResponseEntity<Object> migrateAll(@AuthenticationPrincipal Principal principal) {

    ResponseEntity<Object> errorResponse = checkAuthorized(principal);
    if (errorResponse != null) return errorResponse;

    return startAsyncTaskIfNotBusy("MIGRATION", () -> migrateService.migrateAll());
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Migrate authoritative species lists", tags = "Migrate")
  @GetMapping("/admin/migrate/authoritative")
  public ResponseEntity<Object> migrateAuthoritative(@AuthenticationPrincipal Principal principal) {

    ResponseEntity<Object> errorResponse = checkAuthorized(principal);
    if (errorResponse != null) return errorResponse;

    return startAsyncTaskIfNotBusy("MIGRATION", () -> migrateService.migrateAuthoritative());
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Migrate species lists with custom query", tags = "Migrate")
  @PostMapping(value ="/admin/migrate/custom", consumes = {MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<Object> migrateCustom(@RequestBody CustomLegacyQuery query, @AuthenticationPrincipal Principal principal) {

    ResponseEntity<Object> errorResponse = checkAuthorized(principal);
    if (errorResponse != null) return errorResponse;

    return startAsyncTaskIfNotBusy("MIGRATION", () -> migrateService.migrateCustom(query.getQuery()));
  }

  @NotNull
  private ResponseEntity<Object> startAsyncTaskIfNotBusy(String name, Runnable runnable) {
    if (asyncTask == null || asyncTask.isDone()) {
      asyncTask = CompletableFuture.runAsync(runnable);
      taskName = name;
      return new ResponseEntity<>(HttpStatus.OK);
    } else {
      logger.warn("Already running...");
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    }
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Migrate data from local storage", tags = "Migrate")
  @GetMapping("/migrate-local")
  public ResponseEntity<Object> migrateLocal(@AuthenticationPrincipal Principal principal) {

    ResponseEntity<Object> errorResponse = checkAuthorized(principal);
    if (errorResponse != null) return errorResponse;

    if (asyncTask == null || asyncTask.isDone()) {
      asyncTask =
          CompletableFuture.runAsync(
              () -> {
                migrateService.migrateLocal();
              });
      taskName = "MIGRATION";
      return new ResponseEntity<>(HttpStatus.OK);
    } else {
      logger.warn("Already running...");
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    }
  }
}
