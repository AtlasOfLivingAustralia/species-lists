package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Service
public class MigrateService {

  private static final Logger logger = LoggerFactory.getLogger(MigrateService.class);

  @Autowired SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired UploadService uploadService;
  @Autowired ReleaseService releaseService;
  @Autowired ProgressService progressService;
  @Autowired UserdetailsService userdetailsService;

  String tempDir;
  String migrateUrl;

  private final RestTemplate restTemplate;
  private final String AUTHORITATIVE_LISTS = "/ws/speciesList?isAuthoritative=eq:true&max=1000";
  private final String ALL_LISTS = "/ws/speciesList?max=1000";

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

  private List<SpeciesList> fetchLegacyLists(String query, int offset) {
    try {
      ResponseEntity<Map> response =
              restTemplate.exchange(
                      migrateUrl + query + "&offset=" + offset,
                      HttpMethod.GET,
                      null,
                      Map.class);

      logger.info("Fetching legacy lists: " + migrateUrl + query + "&offset=" + offset);

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

  private List<SpeciesList> getLegacyLists(String query, Boolean skipExisting) {
    int offset = 0;
    boolean done = false;
    List<SpeciesList> lists = new ArrayList<>();

    while (!done) {
      List<SpeciesList> batch = fetchLegacyLists(query, offset);
      lists.addAll(skipExisting ? filterExistingLists(batch, false) : batch);

      if (batch.size() < 1000) {
        done = true;
      } else {
        offset += 1000;
      }
    }

    return lists;
  }

  private List<SpeciesList> getLegacyLists(String query) {
    return getLegacyLists(query, true);
  }

  private List<SpeciesList> filterExistingLists(List<SpeciesList> lists, boolean doesExist) {
    List<String> dataResources = lists.stream().map(SpeciesList::getDataResourceUid).toList();
    Set<String> existingDrUIDs = speciesListMongoRepository
            .findAllByDataResourceUidIsIn(dataResources)
            .stream().map(SpeciesList::getDataResourceUid).collect(Collectors.toSet());

    return lists.stream().filter(list -> existingDrUIDs.contains(list.getDataResourceUid()) == doesExist).toList();
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
      speciesList.setOwner((String) list.get("username"));

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

  private void updateLegacyUserDetails(SpeciesList list, HashMap<String, Map> foundUsers) {
    Map legacyUser;

    if (foundUsers.containsKey(list.getOwner())) {
      legacyUser = foundUsers.get(list.getOwner());
    } else {
      legacyUser = userdetailsService.fetchUserByEmail(list.getOwner());
      foundUsers.put(list.getOwner(), legacyUser);
    }

    if (legacyUser != null) {
      list.setOwner((String)legacyUser.get("userId"));
      list.setOwnerName((String)legacyUser.get("displayName"));
    }
  }

  public void migrateAll() {
    logger.info("Starting migration of ALL lists...");
    migration(getLegacyLists(ALL_LISTS));
  }

  public void migrateAuthoritative() {
    logger.info("Starting migration of AUTHORITATIVE lists...");
    migration(getLegacyLists(AUTHORITATIVE_LISTS));
  }

  public void migrateCustom(String query) {
    logger.info("Starting CUSTOM migration of lists with query: " + query);
    migration(getLegacyLists(query));
  }

  public void migration(List<SpeciesList> speciesLists) {
    progressService.setupMigrationProgress(speciesLists.size());

    // Create a map to store already retrieved user info
    HashMap<String, Map> foundUsers = new HashMap<>();

    speciesLists.forEach(
        speciesList -> {
          logger.info("Downloading file for {}" + speciesList.getDataResourceUid());
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

            updateLegacyUserDetails(speciesList, foundUsers);

            SpeciesList savedList = speciesListMongoRepository.save(speciesList);

            progressService.updateMigrationProgress(savedList);

            IngestJob ingestJob =
                uploadService.loadCSV(
                    savedList.getId(), new FileInputStream(localFile), false, false, true);

            savedList.setRowCount(ingestJob.getRowCount());
            savedList.setFieldList(ingestJob.getFieldList());
            savedList.setFacetList(ingestJob.getFacetList());
            savedList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
            savedList.setDistinctMatchCount(ingestJob.getDistinctMatchCount());

            speciesListMongoRepository.save(savedList);

            // releaseService.release(speciesList.getId());
          } catch (Exception e) {
            logger.error("Download for " + speciesList.getDataResourceUid() + "failed");
            logger.error(e.getMessage(), e);
          }
        });

    progressService.clearMigrationProgress();
  }

  public void syncUserdeatils() {
    List<SpeciesList> existingLegacyLists = filterExistingLists(getLegacyLists(ALL_LISTS, false), true);
    progressService.setupMigrationProgress(existingLegacyLists.size());

    // Create a map to store already retrieved user info
    HashMap<String, Map> foundUsers = new HashMap<>();

    existingLegacyLists.forEach(list -> {
      progressService.updateMigrationProgress(list);

      Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByDataResourceUid(list.getDataResourceUid());
      if (optionalSpeciesList.isPresent()) {
        SpeciesList speciesList = optionalSpeciesList.get();
        speciesList.setOwner(list.getOwner());

        updateLegacyUserDetails(speciesList, foundUsers);
        speciesListMongoRepository.save(speciesList);
      } else {
        logger.info("Legacy list {} ({}) not found in system, skipping...", list.getTitle(), list.getDataResourceUid());
      }
    });

    progressService.clearMigrationProgress();
  }
}
