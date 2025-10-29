package au.org.ala.listsapi.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.oauth2.sdk.token.AccessToken;

import au.org.ala.listsapi.model.ConstraintListItem;
import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.auth.WebService;
import au.org.ala.ws.security.TokenService;
import jakarta.annotation.PostConstruct;

@Service
public class MigrateService {

    private static final Logger logger = LoggerFactory.getLogger(MigrateService.class);

    @Autowired
    SpeciesListMongoRepository speciesListMongoRepository;
    @Autowired
    UploadService uploadService;
    @Autowired
    ReleaseService releaseService;
    @Autowired
    ProgressService progressService;
    @Autowired
    UserdetailsService userdetailsService;
    @Autowired
    WebService webService;
    @Autowired
    TokenService tokenService;
    @Autowired
    ValidationService validationService;
    @Autowired(required = false)
    S3Service s3Service;

    @Value("${aws.s3.enabled:false}")
    private boolean s3Enabled;

    @Value("${temp.dir:/tmp}")
    private String tempDir;

    String migrateUrl;

    private final RestTemplate restTemplate;

    private String defaultLicence;

    @PostConstruct
    private void initializeDefaultLicence() {
        List<ConstraintListItem> licenceValues = validationService.getConstraintsByKey(ConstraintType.licence);
        // Get the second licence value (first is CC0) if it exists, otherwise default to "CC-BY"
        defaultLicence = (licenceValues != null && !licenceValues.isEmpty()) ? licenceValues.get(1).getValue() : "CC-BY";
    }

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

    private List<SpeciesList> fetchLegacyLists(int offset) {
        try {
            logger.info("Fetching legacy species lists from {}", migrateUrl);
            Map params = Map.of("offset", String.valueOf(offset), "max", "1000", "includePrivate", "true");
            Map request = webService.get(
                    migrateUrl + "/ws/speciesListInternal",
                    params,
                    ContentType.APPLICATION_JSON,
                    true,
                    false,
                    null);

            if ((int) request.get("statusCode") == 200) {
                Map response = (Map) request.get("resp");
                List<Map<String, Object>> lists = (List<Map<String, Object>>) response.get("lists");

                return lists.stream().map(this::mapListToSpeciesList).filter(Objects::nonNull).toList();
            }

            throw new Error("Got non-success response from legacy lists API call using URL " + migrateUrl + "/ws/speciesListInternal.");

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    private List<SpeciesList> getLegacyLists(Boolean skipExisting) {
        int offset = 0;
        int total = 0;
        boolean done = false;
        List<SpeciesList> lists = new ArrayList<>();

        while (!done) {
            List<SpeciesList> batch = fetchLegacyLists(offset);
            lists.addAll(skipExisting ? filterExistingLists(batch, false) : batch);

            if (batch.size() < 1000) {
                done = true;
            } else {
                offset += 1000;
            }

            total += batch.size();
        }

        logger.info("Retrieved {} new legacy lists for migration ({} total)", lists.size(), total);

        return lists;
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
            speciesList.setLicence(defaultLicence);

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
            list.setOwner((String) legacyUser.get("userId"));
            list.setOwnerName((String) legacyUser.get("displayName"));
        }
    }

    public void migrateAll() {
        logger.info("Starting migration of ALL lists...");
        migration(getLegacyLists(true));
    }

    public void migration(List<SpeciesList> speciesLists) {
        progressService.setupMigrationProgress(speciesLists.size());

        // Create a map to store already retrieved user info
        HashMap<String, Map> foundUsers = new HashMap<>();

        speciesLists.forEach(
                speciesList -> {
                    logger.info("Downloading file for {}", speciesList.getDataResourceUid());
                    String downloadUrl = migrateUrl
                            + "/ws/speciesListInternal/download/"
                            + speciesList.getDataResourceUid()
                            + "?fetch=%7BkvpValues%3Dselect%7";

                    if (s3Enabled) {
                        String s3Key = null;
                        try {
                            String filename = "species-list-migrate-" + speciesList.getDataResourceUid() + ".csv";

                            AccessToken token = tokenService.getAuthToken(false, null, null);

                            // Download directly to S3
                            s3Key = restTemplate.execute(
                                    downloadUrl,
                                    HttpMethod.GET,
                                    request -> request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                                            token.toAuthorizationHeader()),
                                    response -> {
                                        try (InputStream is = response.getBody()) {
                                            // Upload stream directly to S3
                                            long contentLength = response.getHeaders().getContentLength();
                                            if (contentLength < 0) {
                                                // If content length is unknown, we need to read to byte array first
                                                byte[] data = is.readAllBytes();
                                                return s3Service.uploadFile(
                                                    new java.io.ByteArrayInputStream(data),
                                                    filename,
                                                    "text/csv",
                                                    data.length
                                                );
                                            } else {
                                                return s3Service.uploadFile(is, filename, "text/csv", contentLength);
                                            }
                                        }
                                    });

                            logger.info("File uploaded to S3: {} with key: {}", filename, s3Key);

                            updateLegacyUserDetails(speciesList, foundUsers);

                            SpeciesList savedList = speciesListMongoRepository.save(speciesList);

                            progressService.updateMigrationProgress(savedList);

                            // Get S3 file stream for processing
                            var inputStreamOptional = s3Service.getFileStream(s3Key);
                            if (inputStreamOptional.isEmpty()) {
                                throw new Exception("Failed to retrieve file from S3: " + s3Key);
                            }

                            IngestJob ingestJob = uploadService.loadCSV(
                                    savedList.getId(), inputStreamOptional.get(), false, false, true);

                            savedList.setRowCount(ingestJob.getRowCount());
                            savedList.setFieldList(ingestJob.getFieldList());
                            savedList.setFacetList(ingestJob.getFacetList());
                            savedList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
                            savedList.setDistinctMatchCount(ingestJob.getDistinctMatchCount());

                            speciesListMongoRepository.save(savedList);

                            // releaseService.release(speciesList.getId());
                        } catch (Exception e) {
                            logger.error("Download for " + speciesList.getDataResourceUid() + " failed");
                            logger.error(e.getMessage(), e);

                            if (s3Key != null) {
                                logger.info("S3 file will be cleaned up by lifecycle policy after error: {}", s3Key);
                            }
                        }
                    } else {
                        try {
                            File localFile = new File(
                                    tempDir + "/species-list-migrate-" + speciesList.getDataResourceUid() + ".csv");

                            AccessToken token = tokenService.getAuthToken(false, null, null);

                            restTemplate.execute(
                                    downloadUrl,
                                    HttpMethod.GET,
                                    request -> request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                                            token.toAuthorizationHeader()),
                                    response -> {
                                        try (InputStream is = response.getBody()) {
                                            FileUtils.copyInputStreamToFile(is, localFile);
                                            return null;
                                        }
                                    });

                            updateLegacyUserDetails(speciesList, foundUsers);

                            SpeciesList savedList = speciesListMongoRepository.save(speciesList);

                            progressService.updateMigrationProgress(savedList);

                            IngestJob ingestJob = uploadService.loadCSV(
                                    savedList.getId(), new FileInputStream(localFile), false, false, true);

                            savedList.setRowCount(ingestJob.getRowCount());
                            savedList.setFieldList(ingestJob.getFieldList());
                            savedList.setFacetList(ingestJob.getFacetList());
                            savedList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
                            savedList.setDistinctMatchCount(ingestJob.getDistinctMatchCount());

                            speciesListMongoRepository.save(savedList);

                            // Clean up local file after processing
                            if (localFile.exists()) {
                                localFile.delete();
                                logger.info("Cleaned up local file: {}", localFile.getName());
                            }

                            // releaseService.release(speciesList.getId());
                        } catch (Exception e) {
                            logger.error("Download for " + speciesList.getDataResourceUid() + " failed");
                            logger.error(e.getMessage(), e);
                        }
                    }
                });

        progressService.clearMigrationProgress();
    }
}
