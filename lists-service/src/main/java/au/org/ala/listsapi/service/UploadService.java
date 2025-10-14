/**
 * Copyright (c) 2025 Atlas of Living Australia
 * All Rights Reserved.
 * 
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.listsapi.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import au.org.ala.listsapi.controller.AuthUtils;
import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.InputSpeciesList;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.ws.security.profile.AlaUserProfile;

@Service
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);
    @Autowired
    protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
    @Autowired
    protected SpeciesListMongoRepository speciesListMongoRepository;
    @Autowired
    protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
    @Autowired
    protected TaxonService taxonService;
    @Autowired
    protected ReleaseService releaseService;
    @Autowired
    protected MetadataService metadataService;
    @Autowired
    protected AuthUtils authUtils;
    @Autowired
    protected ProgressService progressService;
    @Autowired
    protected SearchHelperService searchHelperService;
    @Autowired(required = false)
    protected S3Service s3Service;

    @Value("${aws.s3.enabled:false}")
    private boolean s3Enabled;

    @Value("${temp.dir:/tmp}")
    private String tempDir;

    private static final Set<String> NULL_VALUES = new HashSet<>();

    static {
        NULL_VALUES.add("null");
        NULL_VALUES.add("undefined");
        NULL_VALUES.add("na");
        NULL_VALUES.add("n/a");
        NULL_VALUES.add("none");
        NULL_VALUES.add("unknown");
        NULL_VALUES.add("unspecified");
        NULL_VALUES.add("not specified");
    }

    private static final Set<String> ACCEPTED_FILE_TYPES = Set.of("text/csv", "application/zip");

    public static Set<String> getAcceptedFileTypes() {
        return ACCEPTED_FILE_TYPES;
    }


    /**
     * Returns the first non-empty string from the provided arguments.
     */
    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    public boolean deleteList(String speciesListID, AlaUserProfile userProfile) {

        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID,
                speciesListID);
        if (optionalSpeciesList.isPresent() && authUtils.isAuthorized(optionalSpeciesList.get(), userProfile)) {
            String ID = optionalSpeciesList.get().getId();
            logger.info("Deleting speciesListID " + speciesListID);
            speciesListIndexElasticRepository.deleteSpeciesListItemBySpeciesListID(ID);
            speciesListItemMongoRepository.deleteBySpeciesListID(ID);
            speciesListMongoRepository.deleteById(ID);
            logger.info("Deleted speciesListID " + speciesListID);
            return true;
        } else {
            return false;
        }
    }

    public SpeciesList ingest(
            AlaUserProfile user, InputSpeciesList speciesListMetadata, String fileIdentifier, boolean dryRun)
            throws Exception {

        // create the species list in mongo
        SpeciesList speciesList = new SpeciesList();
        speciesList.setOwner(user.getUserId());

        if (user.getGivenName() != null && user.getFamilyName() != null) {
            speciesList.setOwnerName(user.getGivenName() + " " + user.getFamilyName());
        }

        extractUpdates(speciesListMetadata, speciesList);

        // If the species list is public, or authoritative, create a metadata link
        if (!speciesList.getIsPrivate() || speciesList.getIsAuthoritative()) {
            metadataService.setMeta(speciesList);
        }

        speciesList = speciesListMongoRepository.save(speciesList);

        final SpeciesList ingestList = speciesList;

        if (s3Enabled) {
            CompletableFuture.runAsync(
                    () -> {
                        try {
                            asyncIngestS3(ingestList, fileIdentifier, dryRun, false);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } else {
            File fileToLoad = new File(tempDir + "/" + fileIdentifier);
            CompletableFuture.runAsync(
                    () -> {
                        try {
                            asyncIngest(ingestList, fileToLoad, dryRun, false);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        // releaseService.release(speciesList.getId());
        return speciesList;
    }

    private void extractUpdates(InputSpeciesList speciesListMetadata, SpeciesList speciesList) {
        speciesList.setAuthority(speciesListMetadata.getAuthority());
        speciesList.setDescription(speciesListMetadata.getDescription());
        speciesList.setIsAuthoritative(Boolean.parseBoolean(speciesListMetadata.getIsAuthoritative()));
        speciesList.setIsInvasive(Boolean.parseBoolean(speciesListMetadata.getIsInvasive()));
        speciesList.setIsPrivate(Boolean.parseBoolean(speciesListMetadata.getIsPrivate()));
        speciesList.setIsThreatened(Boolean.parseBoolean(speciesListMetadata.getIsThreatened()));
        speciesList.setIsSDS(Boolean.parseBoolean(speciesListMetadata.getIsSDS()));
        speciesList.setIsBIE(Boolean.parseBoolean(speciesListMetadata.getIsBIE()));
        speciesList.setIsThreatened(Boolean.parseBoolean(speciesListMetadata.getIsThreatened()));
        speciesList.setIsInvasive(Boolean.parseBoolean(speciesListMetadata.getIsInvasive()));
        speciesList.setLicence(speciesListMetadata.getLicence());
        speciesList.setListType(speciesListMetadata.getListType());
        speciesList.setRegion(speciesListMetadata.getRegion());
        speciesList.setTitle(speciesListMetadata.getTitle());
        speciesList.setTags(speciesListMetadata.getTags());
    }

    public Boolean isAcceptedFileType(MultipartFile file) {
        try {
            String contentType = determineContentType(file);
            if (contentType != null && ACCEPTED_FILE_TYPES.contains(contentType)) {
                return true;
            }
        } catch (Exception e) {
            logger.error("Error determining content type", e);
        }
        return false;
    }

    public String determineContentType(MultipartFile file) {
        String contentType = file.getContentType();
        logger.info("Initial content type: {}", contentType);

        if (contentType == null || contentType.equals("application/octet-stream")) {
            String filename = file.getOriginalFilename();

            if (filename != null && filename.contains(".")) {
                String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
                for (String acceptedType : ACCEPTED_FILE_TYPES) {
                    if (acceptedType.endsWith(ext)) {
                        return acceptedType;
                    }
                }
                
                return null;
            }
        }

        logger.info("Final content type: {}", contentType);

        return contentType;
    }



    public String uploadFile(MultipartFile file) throws Exception {
        if (s3Enabled) {
            logger.debug("Uploading file to S3: {}", file.getOriginalFilename());
            return s3Service.uploadFile(file);
        } else {
            logger.debug("Uploading file to local temp: {}", file.getOriginalFilename());
            File tempFile = getFileTemp(file);
            file.transferTo(tempFile);
            return tempFile.getName();
        }
    }

    public File getFileTemp(MultipartFile file) {
        return new File(
                tempDir
                        + "/upload-"
                        + System.currentTimeMillis()
                        + "-"
                        + Objects.requireNonNull(file.getOriginalFilename()).replaceAll("[^a-zA-Z0-9._-]", "_"));
    }

    public SpeciesList reload(String speciesListID, String fileIdentifier, boolean dryRun) {
        if (speciesListID != null) {
            // remove any existing progress
            progressService.clearIngestProgress(speciesListID);

            Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository
                    .findByIdOrDataResourceUid(speciesListID, speciesListID);
            if (optionalSpeciesList.isPresent()) {
                SpeciesList speciesList = optionalSpeciesList.get();

                // delete from index
                speciesListIndexElasticRepository.deleteSpeciesListItemBySpeciesListID(speciesList.getId());

                // delete from mongo
                speciesListItemMongoRepository.deleteBySpeciesListID(speciesList.getId());

                final SpeciesList ingestList = speciesList;

                if (s3Enabled) {
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    asyncIngestS3(ingestList, fileIdentifier, dryRun, false);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                } else {
                    File fileToLoad = new File(tempDir + "/" + fileIdentifier);
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    asyncIngest(ingestList, fileToLoad, dryRun, false);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }

                // releaseService.release(speciesList.getId());
                return speciesList;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public IngestJob upload(String fileIdentifier, MultipartFile file)
            throws Exception { 

        IngestJob ingestJob = null;

        if (s3Enabled) {
            String contentType = determineContentType(file);

            // handle CSV
            if (contentType.equals("text/csv")) {
                // load a CSV
                ingestJob = ingestCSVS3(null, fileIdentifier, true, true);
            }

            // handle zip file
            if (contentType.equals("application/zip")) {
                // load a zip file
                ingestJob = ingestZipS3(null, fileIdentifier, true, true);
            }

            if (ingestJob != null) {
                ingestJob.setLocalFile(fileIdentifier);
                return ingestJob;
            } else {
                // Controller should handle this exception 
                throw new Exception("Ingest failed for file: " + fileIdentifier);
            }
        } else {
            File fileToLoad = new File(tempDir + "/" + fileIdentifier);

            Path path = fileToLoad.toPath();
            String mimeType = Files.probeContentType(path);

            // handle CSV
            if (mimeType.equals("text/csv")) {
                // load a CSV
                ingestJob = ingestCSV(null, fileToLoad, true, true);
            }

            // handle zip file
            if (mimeType.equals("application/zip")) {
                try (ZipFile zipFile = new ZipFile(fileToLoad)) {
                    // load a zip file
                    ingestJob = ingestZip(null, zipFile, true, true);
                }
            }

            if (ingestJob != null) {
                ingestJob.setLocalFile(fileToLoad.getName());
                return ingestJob;
            } else {
                // Controller should handle this exception 
                throw new Exception("Ingest failed for file: " + fileIdentifier);
            }
        }
    }

    public void asyncIngestS3(
            SpeciesList speciesList, String s3Key, boolean dryRun, boolean skipIndexing)
            throws Exception {

        IngestJob ingestJob = null;

        String contentType = s3Service.getContentType(s3Key);
        String originalFilename = s3Service.getOriginalFilename(s3Key);

        try {
            // handle CSV
            if (contentType.equals("text/csv")) {
                // load a CSV
                ingestJob = ingestCSVS3(speciesList.getId(), s3Key, dryRun, skipIndexing);
            }

            // handle zip file
            if (contentType.equals("application/zip")) {
                // load a zip file
                ingestJob = ingestZipS3(speciesList.getId(), s3Key, dryRun, skipIndexing);
            }

            if (ingestJob != null) {
                ingestJob.setLocalFile(originalFilename);

                speciesList.setFacetList(ingestJob.getFacetList());
                speciesList.setFieldList(ingestJob.getFieldList());
                speciesList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
                speciesList.setRowCount(ingestJob.getRowCount());
                speciesList.setDistinctMatchCount(ingestJob.getDistinctMatchCount());
                speciesList.setLastUpdated(new Date());

                speciesListMongoRepository.save(speciesList);

                logger.info("Async S3 ingestion complete... " + speciesList);
            }
        } finally {
            if (!dryRun) {
                logger.info("S3 file will be cleaned up by lifecycle policy: {}", s3Key);
            }
        }
    }

    public void asyncIngest(
            SpeciesList speciesList, File fileToLoad, boolean dryRun, boolean skipIndexing)
            throws Exception {

        IngestJob ingestJob = null;

        Path path = fileToLoad.toPath();
        String mimeType = Files.probeContentType(path);

        // handle CSV
        if (mimeType.equals("text/csv")) {
            // load a CSV
            ingestJob = ingestCSV(speciesList.getId(), fileToLoad, dryRun, skipIndexing);
        }

        // handle zip file
        if (mimeType.equals("application/zip")) {
            try (ZipFile zipFile = new ZipFile(fileToLoad)) {
                // load a zip file
                ingestJob = ingestZip(speciesList.getId(), zipFile, dryRun, skipIndexing);
            }
        }

        if (ingestJob != null) {
            ingestJob.setLocalFile(fileToLoad.getName());

            speciesList.setFacetList(ingestJob.getFacetList());
            speciesList.setFieldList(ingestJob.getFieldList());
            speciesList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
            speciesList.setRowCount(ingestJob.getRowCount());
            speciesList.setDistinctMatchCount(ingestJob.getDistinctMatchCount());
            speciesList.setLastUpdated(new Date());

            speciesListMongoRepository.save(speciesList);

            logger.info("Async ingestion complete... " + speciesList);
        }
    }

    public IngestJob ingestCSVS3(String speciesListID, String s3Key, boolean dryRun, boolean skipIndexing)
            throws Exception {
        var inputStreamOptional = s3Service.getFileStream(s3Key);
        if (inputStreamOptional.isEmpty()) {
            throw new Exception("File not found in S3: " + s3Key);
        }
        return loadCSV(speciesListID, inputStreamOptional.get(), dryRun, skipIndexing, false);
    }

    public IngestJob ingestZipS3(
            String speciesListID, String s3Key, boolean dryRun, boolean skipIndexing)
            throws Exception {
        var inputStreamOptional = s3Service.getFileStream(s3Key);
        if (inputStreamOptional.isEmpty()) {
            throw new Exception("File not found in S3: " + s3Key);
        }

        java.io.File tempFile = java.io.File.createTempFile("temp-zip", ".zip");
        try (InputStream s3Stream = inputStreamOptional.get();
             java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            s3Stream.transferTo(fos);
        }

        try (ZipFile zipFile = new ZipFile(tempFile)) {
            // get the field list
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".csv")) {
                    return loadCSV(speciesListID, zipFile.getInputStream(entry), dryRun, skipIndexing, false);
                }
            }
        } finally {
            tempFile.delete();
        }
        return null;
    }

    public IngestJob ingestCSV(String speciesListID, File file, boolean dryRun, boolean skipIndexing)
            throws Exception {
        return loadCSV(speciesListID, new FileInputStream(file), dryRun, skipIndexing, false);
    }

    public IngestJob ingestZip(
            String speciesListID, ZipFile zipFile, boolean dryRun, boolean skipIndexing)
            throws Exception {
        // get the field list
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().endsWith(".csv")) {
                return loadCSV(speciesListID, zipFile.getInputStream(entry), dryRun, skipIndexing, false);
            }
        }
        return null;
    }

    public IngestJob loadCSV(
            String speciesListID,
            InputStream inputStream,
            boolean dryRun,
            boolean skipIndexing,
            boolean isMigration)
            throws Exception {

        if (!dryRun && speciesListID != null) {
            long findByIdStart = System.nanoTime();
            Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListID);
            long findByIdElapsed = (System.nanoTime() - findByIdStart) / 1000000;
            logger.info("[{}|loadCSV] Fetching species list took {}ms", speciesListID, findByIdElapsed);
            if (speciesList.isEmpty()) {
                throw new Exception("Species list not found");
            }
        }

        int rowCount = 0;
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        MappingIterator<Map<String, String>> iterator = mapper.reader(Map.class).with(schema).readValues(inputStream);

        // store
        Map<String, Set<String>> facets = new HashMap<>();
        Set<String> notFacetable = new HashSet<>();
        Set<String> fieldNames = new HashSet<>();

        int recordsWithoutScientificName = 0;

        List<String> originalFieldNames = new ArrayList<>();
        List<SpeciesListItem> batch = new ArrayList<>();

        long iteratorStart = System.nanoTime();
        while (iterator.hasNext()) {

            Map<String, String> values = iterator.next();

            if (isMigration) {
                // remove legacy data
                values.remove("guid");
                values.remove("scientificName");
                values.remove("family");
                values.remove("kingdom");
            }

            if (originalFieldNames.isEmpty()) {
                originalFieldNames.addAll(values.keySet());
            }

            String scientificName = values.remove(DwcTerm.scientificName.simpleName());
            String taxonID = values.remove(DwcTerm.taxonID.simpleName());
            String taxonConceptID = values.remove(DwcTerm.taxonConceptID.simpleName());
            String vernacularName = values.remove(DwcTerm.vernacularName.simpleName());

            String suppliedName = values.remove("Supplied Name");

            if (suppliedName != null) {
                scientificName = suppliedName; // undocumented input field, left in for backward compatibility
            } else {
                suppliedName = firstNonEmpty(scientificName, taxonID, taxonConceptID, vernacularName);
            }

            if (StringUtils.isEmpty(scientificName)
                    && StringUtils.isEmpty(vernacularName)
                    && StringUtils.isEmpty(taxonID)
                    && StringUtils.isEmpty(taxonConceptID)) {
                recordsWithoutScientificName++;
            }

            String kingdom = values.remove(DwcTerm.kingdom.simpleName());
            String phylum = values.remove(DwcTerm.phylum.simpleName());
            String classs = values.remove(DwcTerm.class_.simpleName());
            String order = values.remove(DwcTerm.order.simpleName());
            String family = values.remove(DwcTerm.family.simpleName());
            String genus = values.remove(DwcTerm.genus.simpleName());

            // process remaining fields (user supplied KVP data)
            List<KeyValue> keyValues = new ArrayList<>();
            Map<String, String> properties = new HashMap<>();
            values.entrySet().stream()
                    .forEach(
                            e -> {
                                keyValues.add(new KeyValue(cleanKey(e.getKey()), e.getValue()));
                                properties.put(cleanKey(e.getKey()), e.getValue());
                                fieldNames.add(cleanKey(e.getKey()));

                                if (!notFacetable.contains(cleanKey(e.getKey()))) {
                                    if (e.getValue() != null && e.getValue().length() > 30) {
                                        notFacetable.add(cleanKey(e.getKey()));
                                        logger.info(
                                                e.getKey()
                                                        + " has values greater than 30 characters. Marking as not facet-able. Example : "
                                                        + e.getValue());
                                    } else {
                                        Set<String> distinctValues = facets.getOrDefault(cleanKey(e.getKey()),
                                                new HashSet<>());
                                        distinctValues.add(e.getValue());
                                        facets.put(cleanKey(e.getKey()), distinctValues);
                                        if (distinctValues.size() > 30) {
                                            notFacetable.add(cleanKey(e.getKey()));
                                            logger.info(
                                                    e.getKey()
                                                            + " has more than 30 distinct values. Marking as not facetable");
                                        }
                                    }
                                }
                            });

            if (!dryRun && speciesListID != null) {

                // write to mongo
                SpeciesListItem speciesListItem = new SpeciesListItem(
                        null,
                        0,
                        speciesListID,
                        cleanField(taxonID),
                        cleanField(suppliedName),
                        cleanField(scientificName),
                        cleanField(vernacularName),
                        cleanField(kingdom),
                        cleanField(phylum),
                        cleanField(classs),
                        cleanField(order),
                        cleanField(family),
                        cleanField(genus),
                        keyValues,
                        null, // classification
                        new Date(), // dateCreated
                        new Date(), // lastUpdated,
                        null);

                batch.add(speciesListItem);

                if (batch.size() == 10000) {
                    long iteratorSavingStart = System.nanoTime();
                    searchHelperService.speciesListItemsBulkSave(batch);
                    long iteratorSavingElapsed = (System.nanoTime() - iteratorSavingStart) / 1000000;
                    logger.info("[{}|loadCSV] Iterator saving took {}ms", speciesListID, iteratorSavingElapsed);
                    batch.clear();
                }
            }
            rowCount++;
        }
        long iteratorElapsed = (System.nanoTime() - iteratorStart) / 1000000;
        logger.info("[{}|loadCSV] Iterator took {}ms", speciesListID, iteratorElapsed);

        if (!batch.isEmpty()) {
            long batchSavingStart = System.nanoTime();
            searchHelperService.speciesListItemsBulkSave(batch);
            long batchSavingElapsed = (System.nanoTime() - batchSavingStart) / 1000000;
            logger.info("[{}|loadCSV] Batch saving took {}ms", speciesListID, batchSavingElapsed);
            batch.clear();
        }
        logger.info("[{}|loadCSV] Species list loaded into database", speciesListID);

        IngestJob ingestJob = new IngestJob();
        logger.info("Field names = " + StringUtils.join(fieldNames, ", "));
        List<String> facetNames = new ArrayList<>(fieldNames);
        facetNames.removeAll(notFacetable);

        logger.info("Facet-able names = " + StringUtils.join(facetNames, ", "));
        ingestJob.setFieldList(fieldNames.stream().toList());
        ingestJob.setFacetList(facetNames);
        ingestJob.setRowCount(rowCount);
        ingestJob.setOriginalFieldNames(originalFieldNames);

        List<String> validationError = new ArrayList<>();

        if (rowCount == 0) {
            validationError.add("NO_RECORDS_IN_CSV");
        }

        if (recordsWithoutScientificName > 0) {
            if (recordsWithoutScientificName == rowCount) {
                validationError.add("ALL_RECORDS_WITHOUT_SCIENTIFIC_NAME");
            } else {
                validationError.add("SOME_RECORDS_WITHOUT_SCIENTIFIC_NAME");
            }
        }

        if (!validationError.isEmpty()) {
            ingestJob.setValidationErrors(validationError);
        }

        if (!dryRun && !skipIndexing) {
            progressService.setupIngestProgress(speciesListID, rowCount);

            long distinctMatchCount = taxonService.taxonMatchDataset(speciesListID);
            ingestJob.setDistinctMatchCount(distinctMatchCount);

            taxonService.reindex(speciesListID);
        }

        return ingestJob;
    }

    public static String cleanField(String value) {
        if (value == null || NULL_VALUES.contains(value.trim().toLowerCase())) {
            return null;
        }
        return value.trim();
    }

    public static String cleanKey(String keyName) {
        try {
            String cleanedName = keyName
                    .replaceAll("[^\\w\\s-+^:,]", "")
                    .replaceAll("__+", "_")
                    .replaceAll(" ", "_")
                    .trim();
            Term term = TermFactory.instance().findTerm(cleanedName);
            return (term != null) ? term.simpleName() : cleanedName;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return keyName;
        }
    }
}
