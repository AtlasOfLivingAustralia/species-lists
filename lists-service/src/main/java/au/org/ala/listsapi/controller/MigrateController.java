package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.ReleaseService;
import au.org.ala.listsapi.service.UploadService;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

/** Services to migrate data from */
@CrossOrigin(origins = "*", maxAge = 3600)
@Controller
public class MigrateController {

  private final SpeciesListMongoRepository speciesListMongoRepository;
  private final UploadService uploadService;
  private final ReleaseService releaseService;
  private final String tempDir;
  private final String migrateUrl;
  private final RestTemplate restTemplate;

  public MigrateController(
      SpeciesListMongoRepository speciesListMongoRepository,
      UploadService uploadService,
      ReleaseService releaseService,
      @Value("${temp.dir:/tmp}") String tempDir,
      @Value("${migrate.url:https://lists.ala.org.au}") String migrateUrl,
      RestTemplate restTemplate) {

    this.speciesListMongoRepository = speciesListMongoRepository;
    this.uploadService = uploadService;
    this.releaseService = releaseService;
    this.tempDir = tempDir;
    this.migrateUrl = migrateUrl;
    this.restTemplate = restTemplate;
  }

  private List<SpeciesList> listOfAuthoritativeLists() {
    try {
      ResponseEntity<Map> response =
          restTemplate.exchange(
              migrateUrl + "/ws/speciesList?isAuthoritative=eq:true&max=1000",
              HttpMethod.GET,
              null,
              Map.class);

      if (response.getStatusCode() == HttpStatus.OK) {
        Map<String, Object> responseMap = response.getBody();
        List<Map<String, Object>> lists = (List<Map<String, Object>>) responseMap.get("lists");

        return lists.stream().map(this::mapListToSpeciesList).filter(Objects::nonNull).toList();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return Collections.emptyList();
  }

  private SpeciesList mapListToSpeciesList(Map<String, Object> list) {
    try {
      SpeciesList speciesList = new SpeciesList();
      speciesList.setDataResourceUid((String) list.get("dataResourceUid"));
      speciesList.setDescription((String) list.get("description"));
      speciesList.setTitle((String) list.get("listName"));
      speciesList.setListType((String) list.get("listType"));
      speciesList.setAuthority((String) list.get("authority"));
      speciesList.setRegion((String) list.get("region"));

      speciesList.setIsAuthoritative(
          list.get("isAuthoritative") instanceof Boolean && (Boolean) list.get("isAuthoritative"));
      speciesList.setIsPrivate(
          list.get("isPrivate") instanceof Boolean && (Boolean) list.get("isPrivate"));
      speciesList.setIsThreatened(
          list.get("isThreatened") instanceof Boolean && (Boolean) list.get("isThreatened"));
      speciesList.setIsInvasive(
          list.get("isInvasive") instanceof Boolean && (Boolean) list.get("isInvasive"));
      speciesList.setWkt((String) list.get("wkt"));

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
      speciesList.setDateCreated((String) list.get("dateCreated") != null ? sdf.parse((String) list.get("dateCreated")) : null);
      speciesList.setLastUploaded((String) list.get("lastUploaded") != null ? sdf.parse((String) list.get("lastUploaded")) : null);
      speciesList.setLastUpdated((String) list.get("lastUpdated") != null ? sdf.parse((String) list.get("lastUpdated")) : null);

      return speciesList;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @GetMapping("/migrate")
  public ResponseEntity<Object> migrate() {
    List<SpeciesList> speciesLists = listOfAuthoritativeLists();
    speciesLists.forEach(
        speciesList -> {
          System.out.println("Downloading file for " + speciesList.getDataResourceUid());
          String downloadUrl =
              migrateUrl
                  + "/speciesListItem/downloadList/"
                  + speciesList.getDataResourceUid()
                  + "?fetch=%7BkvpValues%3Dselect%7";
          try {
            File localFile =
                new File(
                    tempDir + "/species-list-migrate-" + speciesList.getDataResourceUid() + ".csv");
            restTemplate.execute(
                downloadUrl,
                HttpMethod.GET,
                null,
                response -> {
                  try (InputStream is = response.getBody()) {
                    FileUtils.copyInputStreamToFile(is, localFile);
                    return null;
                  }
                });
            speciesListMongoRepository.save(speciesList);
            IngestJob ingestJob =
                uploadService.loadCSV(
                    speciesList.getId(), new FileInputStream(localFile), false, true, true);
            speciesList.setRowCount(ingestJob.getRowCount());
            speciesList.setFieldList(ingestJob.getFieldList());
            speciesList.setFacetList(ingestJob.getFacetList());
            speciesList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
            speciesListMongoRepository.save(speciesList);
            releaseService.release(speciesList.getId());
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @GetMapping("/migrate-local")
  public ResponseEntity<Object> migrateLocal() {
    List<SpeciesList> speciesLists = listOfAuthoritativeLists();
    speciesLists.forEach(
        speciesList -> {
          try {
            File localFile =
                new File(
                    tempDir + "/species-list-migrate-" + speciesList.getDataResourceUid() + ".csv");
            speciesListMongoRepository.save(speciesList);
            IngestJob ingestJob =
                uploadService.loadCSV(
                    speciesList.getId(), new FileInputStream(localFile), false, true, true);
            speciesList.setRowCount(ingestJob.getRowCount());
            speciesList.setFieldList(ingestJob.getFieldList());
            speciesList.setFacetList(ingestJob.getFacetList());
            speciesList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
            speciesListMongoRepository.save(speciesList);
            releaseService.release(speciesList.getId());
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
