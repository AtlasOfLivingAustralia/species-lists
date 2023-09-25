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
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class DownloadController {

  private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);
  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Download a species list", tags = "Download")
  @GetMapping("/download/{speciesListID}")
  public ResponseEntity<Object> download(
          @PathVariable("speciesListID") String speciesListID,
          @AuthenticationPrincipal Principal principal,
          HttpServletResponse response) {
    try {
      Optional<SpeciesList> speciesListOptional = speciesListMongoRepository.findById(speciesListID);
      if (speciesListOptional.isEmpty()) {
        return ResponseEntity.badRequest().body("Unrecognized ID while downloading dataset");
      }

      SpeciesList speciesList = speciesListOptional.get();
      if (speciesList.getIsPrivate()) {
        ResponseEntity<Object> errorResponse = checkAuthorizedToDownload(speciesList, principal);
        if (errorResponse != null) {
          return errorResponse;
        }
      }

      List<String> csvHeaders = new ArrayList<>();
      csvHeaders.add("scientificName");
      csvHeaders.addAll(speciesList.getFieldList());

      setupResponseHeaders(response, speciesListID);

      try (ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream())) {

        ZipEntry zipEntry = new ZipEntry( "taxa.csv");
        zipOutputStream.putNextEntry(zipEntry);

        try (CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(zipOutputStream, StandardCharsets.UTF_8))) {
          writeCsvHeaders(csvWriter, csvHeaders);

          int startIndex = 0;
          int pageSize = 1000;
          Pageable pageable = PageRequest.of(startIndex, pageSize);

          boolean finished = false;
          while (!finished) {
            Page<SpeciesListItem> page = speciesListItemMongoRepository.findBySpeciesListID(speciesListID, pageable);
            if (page.isEmpty()) {
              finished = true;
            } else {
              logger.info("Writing CSV data for species list " + speciesListID + " page " + startIndex + " page size " + page.stream().count());
              writeCsvData(csvWriter, speciesList.getFieldList(), page);
              csvWriter.flush();
              startIndex += 1;
              pageable = PageRequest.of(startIndex, pageSize);
            }
          }
          logger.info("Finished writing CSV data for species list " + speciesListID);
        } catch (Exception e){
          e.printStackTrace();
        }
      } catch (Exception ex){
        ex.printStackTrace();
      }
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Error while attempting to download dataset: " + e.getMessage());
    }
  }

  private void setupResponseHeaders(HttpServletResponse response, String speciesListID) {
    response.setHeader("Content-Type", "application/octet-stream");
    response.setHeader("Content-Disposition", "attachment; filename=species-list-" + speciesListID + ".zip");
  }

  private void writeCsvHeaders(CSVWriter csvWriter, List<String> csvHeaders) {
    csvWriter.writeNext(csvHeaders.toArray(new String[0]));
  }

  private void writeCsvData(CSVWriter csvWriter, List<String> fieldList, Page<SpeciesListItem> page) {
    page.forEach(speciesListItem -> {
      List<String> csvRow = new ArrayList<>();
      csvRow.add(speciesListItem.getScientificName());
      fieldList.forEach(field -> {
        speciesListItem.getProperties().stream()
                .filter(keyValue -> keyValue.getKey().equals(field))
                .findFirst()
                .ifPresent(keyValue -> csvRow.add(keyValue.getValue()));
      });
      csvWriter.writeNext(csvRow.toArray(new String[0]));
    });
  }

  @Nullable
  private ResponseEntity<Object> checkAuthorizedToDownload(
      SpeciesList speciesList, Principal principal) {

    // check user logged in
    AlaUserProfile alaUserProfile = (AlaUserProfile) principal;
    if (alaUserProfile == null) {
      return ResponseEntity.badRequest().body("User not found");
    }

    // check authorised
    if (!AuthUtils.isAuthorized(speciesList, principal)) {
      return ResponseEntity.badRequest().body("User not authorized");
    }
    return null;
  }
}
