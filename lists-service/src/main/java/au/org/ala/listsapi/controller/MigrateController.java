package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.ReleaseService;
import au.org.ala.listsapi.service.TaxonService;
import au.org.ala.listsapi.service.UploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

/** Services to migrate data from */
@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class MigrateController {

  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;

  @Autowired private UploadService uploadService;
  @Autowired private TaxonService taxonService;
  @Autowired private ReleaseService releaseService;

  @Value("${temp.dir:/tmp}")
  private String tempDir;

  @Value("${migrate.url:https://lists.ala.org.au}")
  private String migrateUrl;

  private List<SpeciesList> listOfAuthoritativeLists() throws Exception {

    HttpRequest httpRequest =
        HttpRequest.newBuilder(
                new URI(migrateUrl + "/ws/speciesList?isAuthoritative=eq:true&max=1000"))
            .GET()
            .build();

    HttpResponse<String> response =
        HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .build()
            .send(httpRequest, HttpResponse.BodyHandlers.ofString());

    ObjectMapper objectMapper = new ObjectMapper();

    if (response.statusCode() == HttpStatus.OK.value()) {
      Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);

      List<Map<String, Object>> lists = (List<Map<String, Object>>) responseMap.get("lists");

      List<SpeciesList> speciesLists =
          lists.stream()
              .map(
                  list -> {
                    try {
                      SpeciesList speciesList = new SpeciesList();
                      speciesList.setDataResourceUid((String) list.get("dataResourceUid"));
                      speciesList.setDescription((String) list.get("description"));
                      speciesList.setTitle((String) list.get("listName"));
                      speciesList.setListType((String) list.get("listType"));
                      speciesList.setAuthority((String) list.get("authority"));
                      speciesList.setRegion((String) list.get("region"));

                      speciesList.setIsAuthoritative(
                          list.get("isAuthoritative") == null
                              ? false
                              : (Boolean) list.get("isAuthoritative"));
                      speciesList.setIsPrivate(
                          list.get("isPrivate") == null ? false : (Boolean) list.get("isPrivate"));
                      speciesList.setIsThreatened(
                          list.get("isThreatened") == null
                              ? false
                              : (Boolean) list.get("isThreatened"));
                      speciesList.setIsInvasive(
                          list.get("isInvasive") == null
                              ? false
                              : (Boolean) list.get("isInvasive"));
                      speciesList.setWkt((String) list.get("wkt"));

                      return speciesList;
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                    return null;
                  })
              .collect(Collectors.toList());

      return speciesLists;
    }
    return null;
  }

  @Hidden
  @GetMapping("/migrate")
  public ResponseEntity<Object> migrate() throws Exception {
    try {
      List<SpeciesList> speciesLists = listOfAuthoritativeLists();
      speciesLists.forEach(
          speciesList -> {
            System.out.println("Downloading file for " + speciesList.getDataResourceUid());
            // download
            String downloadUrl =
                migrateUrl
                    + "/speciesListItem/downloadList/"
                    + speciesList.getDataResourceUid()
                    + "?fetch=%7BkvpValues%3Dselect%7";
            try {
              File localFile =
                  new File(
                      tempDir
                          + "/species-list-migrate-"
                          + speciesList.getDataResourceUid()
                          + ".csv");
              FileUtils.copyURLToFile(new URL(downloadUrl), localFile);
              speciesListMongoRepository.save(speciesList);
              IngestJob ingestJob =
                  uploadService.loadCSV(
                      speciesList.getId(), new FileInputStream(localFile), false, true);
              // update the field list
              speciesList.setRowCount(ingestJob.getRowCount());
              speciesList.setFieldList(ingestJob.getFieldList());
              speciesList.setFacetList(ingestJob.getFacetList());
              speciesListMongoRepository.save(speciesList);
            } catch (Exception e) {
              e.printStackTrace();
            }
          });
    } catch (MalformedURLException e) {
      return ResponseEntity.badRequest().body("Error while releasing the file: " + e.getMessage());
    } catch (IOException e) {
      return ResponseEntity.badRequest().body("Error while releasing the file: " + e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Error while releasing the file: " + e.getMessage());
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Hidden
  @GetMapping("/migrate-local")
  public ResponseEntity<Object> migrateLocal() throws Exception {
    try {
      List<SpeciesList> speciesLists = listOfAuthoritativeLists();
      speciesLists.forEach(
          speciesList -> {
            try {
              File localFile =
                  new File(
                      tempDir
                          + "/species-list-migrate-"
                          + speciesList.getDataResourceUid()
                          + ".csv");
              speciesListMongoRepository.save(speciesList);
              HashSet<String> fieldNames = new HashSet<>();
              HashSet<String> facetNames = new HashSet<>();
              IngestJob ingestJob =
                  uploadService.loadCSV(
                      speciesList.getId(), new FileInputStream(localFile), false, true);
              // update the field list
              speciesList.setRowCount(ingestJob.getRowCount());
              speciesList.setFieldList(ingestJob.getFieldList());
              speciesList.setFacetList(ingestJob.getFacetList());

              speciesListMongoRepository.save(speciesList);
            } catch (Exception e) {
              e.printStackTrace();
              ;
            }
          });
    } catch (MalformedURLException e) {
      return ResponseEntity.badRequest()
          .body("Bad URL Error while releasing the file: " + e.getMessage());
    } catch (IOException e) {
      return ResponseEntity.badRequest().body("Error while releasing the file: " + e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Error while releasing the file: " + e.getMessage());
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
