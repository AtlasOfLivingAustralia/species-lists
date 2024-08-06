package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.ws.security.profile.AlaUserProfile;
import com.opencsv.CSVWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DownloadController {

  private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);
  protected final SpeciesListMongoRepository speciesListMongoRepository;
  protected final SpeciesListItemMongoRepository speciesListItemMongoRepository;
  protected final AuthUtils authUtils;

  public static final String[] CLASSIFICATION_HEADER_NAMES = {
    "guid", // taxonID would be better
    "scientificName",
    "genus",
    "family",
    "order",
    "class",
    "phylum",
    "kingdom",
    "vernacularName",
    "matchType",
    "nameType"
  };

  public DownloadController(
      SpeciesListMongoRepository speciesListMongoRepository,
      SpeciesListItemMongoRepository speciesListItemMongoRepository,
      AuthUtils authUtils) {
    this.speciesListMongoRepository = speciesListMongoRepository;
    this.speciesListItemMongoRepository = speciesListItemMongoRepository;
    this.authUtils = authUtils;
  }

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Download a species list in CSV format", tags = "REST")
  @GetMapping("/download/{speciesListID}")
  public ResponseEntity<Object> download(
      @PathVariable("speciesListID") String speciesListID,
      @AuthenticationPrincipal Principal principal,
      @RequestParam(value = "zip", defaultValue = "false") Boolean zipped,
      HttpServletResponse response) {
    try {
      logger.info("Downloading species list " + speciesListID);
      Optional<SpeciesList> speciesListOptional =
          speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
      if (speciesListOptional.isEmpty()) {
        return ResponseEntity.badRequest().body("Unrecognized ID while downloading dataset");
      }

      SpeciesList speciesList = speciesListOptional.get();
      if (speciesList.getIsPrivate()) {
        // if private, check user is logged in and authorised
        ResponseEntity<Object> errorResponse = checkAuthorizedToDownload(speciesList, principal);
        if (errorResponse != null) {
          return errorResponse;
        }
      }

      List<String> csvHeaders = new ArrayList<>();
      csvHeaders.add("Supplied name");
      csvHeaders.addAll(speciesList.getFieldList());
      csvHeaders.addAll(Arrays.asList(CLASSIFICATION_HEADER_NAMES));

      setupResponseHeaders(response, speciesList.getId(), zipped);

      if (zipped) {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream())) {
          ZipEntry zipEntry = new ZipEntry("taxa.csv");
          zipOutputStream.putNextEntry(zipEntry);
          writeCSV(speciesList.getId(), zipOutputStream, csvHeaders, speciesList);
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
          return ResponseEntity.badRequest()
              .body("Error while attempting to download dataset: " + ex.getMessage());
        }
      } else {
        writeCSV(speciesList.getId(), response.getOutputStream(), csvHeaders, speciesList);
      }

      logger.info("Finished writing zip download data for species list " + speciesList.getId());
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body("Error while attempting to download dataset: " + e.getMessage());
    }
  }

  private void writeCSV(
      String speciesListID,
      OutputStream outputStream,
      List<String> csvHeaders,
      SpeciesList speciesList) {
    try (CSVWriter csvWriter =
        new CSVWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
      writeCsvHeaders(csvWriter, csvHeaders);

      int startIndex = 0;
      int pageSize = 1000;
      Pageable pageable = PageRequest.of(startIndex, pageSize);

      boolean finished = false;
      while (!finished) {
        Page<SpeciesListItem> page =
            speciesListItemMongoRepository.findBySpeciesListID(speciesListID, pageable);
        if (page.isEmpty()) {
          finished = true;
        } else {
          logger.info(
              "Writing CSV data for species list "
                  + speciesListID
                  + " page "
                  + startIndex
                  + " page size "
                  + page.stream().count());
          writeCsvData(csvWriter, speciesList.getFieldList(), page);
          csvWriter.flush();
          startIndex += 1;
          pageable = PageRequest.of(startIndex, pageSize);
        }
      }
      logger.info("Finished writing CSV data for species list " + speciesListID);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  private void setupResponseHeaders(
      HttpServletResponse response, String speciesListID, Boolean isZipped) {
    response.setHeader("Content-Type", "application/octet-stream");
    response.setHeader(
        "Content-Disposition",
        "attachment; filename=species-list-" + speciesListID + (isZipped ? ".zip" : ".csv"));
  }

  private void writeCsvHeaders(CSVWriter csvWriter, List<String> csvHeaders) {
    csvWriter.writeNext(csvHeaders.toArray(new String[0]));
  }

  private void writeCsvData(
      CSVWriter csvWriter, List<String> fieldList, Page<SpeciesListItem> page) {
    page.forEach(
        speciesListItem -> {
          List<String> csvRow = new ArrayList<>();
          // add the supplied name
          csvRow.add(speciesListItem.getScientificName());

          fieldList.forEach(
              field ->
                  speciesListItem.getProperties().stream()
                      .filter(keyValue -> keyValue.getKey().equals(field))
                      .findFirst()
                      .ifPresent(keyValue -> csvRow.add(keyValue.getValue())));

          csvRow.add(speciesListItem.getClassification().getTaxonConceptID());
          csvRow.add(speciesListItem.getClassification().getScientificName());
          csvRow.add(speciesListItem.getClassification().getGenus());
          csvRow.add(speciesListItem.getClassification().getFamily());
          csvRow.add(speciesListItem.getClassification().getOrder());
          csvRow.add(speciesListItem.getClassification().getClasss());
          csvRow.add(speciesListItem.getClassification().getPhylum());
          csvRow.add(speciesListItem.getClassification().getKingdom());
          csvRow.add(speciesListItem.getClassification().getVernacularName());
          csvRow.add(speciesListItem.getClassification().getMatchType());
          csvRow.add(speciesListItem.getClassification().getNameType());
          csvWriter.writeNext(csvRow.toArray(new String[0]));
        });
  }

  @Nullable
  private ResponseEntity<Object> checkAuthorizedToDownload(
      SpeciesList speciesList, Principal principal) {

    // check user logged in
    if (authUtils.getUserProfile(principal) == null) {
      return ResponseEntity.badRequest().body("User not logged in");
    }

    // check authorised
    if (!authUtils.isAuthorized(speciesList, principal)) {
      return ResponseEntity.badRequest().body("User not authorized");
    }
    return null;
  }
}
