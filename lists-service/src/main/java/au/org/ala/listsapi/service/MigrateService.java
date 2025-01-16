package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Service
public class MigrateService {

  private static final Logger logger = LoggerFactory.getLogger(MigrateService.class);

  SpeciesListMongoRepository speciesListMongoRepository;
  UploadService uploadService;
  ReleaseService releaseService;
  String tempDir;
  String migrateUrl;

  private final RestTemplate restTemplate;

  public MigrateService(
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
      logger.error(e.getMessage(), e);
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
      speciesList.setLastUpdatedBy((String) list.get("username"));

      if (list.get("lastUpdated") != null) {
        String lastUpdatedString = (String) list.get("lastUpdated");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        speciesList.setLastUpdated(sdf.parse(lastUpdatedString));
      }
      if (list.get("dateCreated") != null) {
        String dateCreatedString = (String) list.get("dateCreated");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        speciesList.setDateCreated(sdf.parse(dateCreatedString));
      }

      speciesList.setIsAuthoritative(
          list.get("isAuthoritative") instanceof Boolean && (Boolean) list.get("isAuthoritative"));
      speciesList.setIsPrivate(
          list.get("isPrivate") instanceof Boolean && (Boolean) list.get("isPrivate"));
      speciesList.setIsThreatened(
          list.get("isThreatened") instanceof Boolean && (Boolean) list.get("isThreatened"));
      speciesList.setIsInvasive(
          list.get("isInvasive") instanceof Boolean && (Boolean) list.get("isInvasive"));
      speciesList.setIsBIE(
          list.get("isBIE") instanceof Boolean && (Boolean) list.get("isBIE"));
      speciesList.setIsSDS(
          list.get("isSDS") instanceof Boolean && (Boolean) list.get("isSDS"));
      speciesList.setWkt((String) list.get("wkt"));

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
      speciesList.setDateCreated(
          list.get("dateCreated") != null ? sdf.parse((String) list.get("dateCreated")) : null);
      speciesList.setLastUploaded(
          list.get("lastUploaded") != null ? sdf.parse((String) list.get("lastUploaded")) : null);
      speciesList.setLastUpdated(
          list.get("lastUpdated") != null ? sdf.parse((String) list.get("lastUpdated")) : null);

      return speciesList;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
    return null;
  }

  public void migrate() {
    List<SpeciesList> speciesLists = listOfAuthoritativeLists();
    speciesLists.forEach(
        speciesList -> {
          logger.info("Downloading file for " + speciesList.getDataResourceUid());
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
            // releaseService.release(speciesList.getId());
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
          }
        });
  }

  @GetMapping("/migrate-local")
  public void migrateLocal() {
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
            // releaseService.release(speciesList.getId());
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
          }
        });
  }
}
