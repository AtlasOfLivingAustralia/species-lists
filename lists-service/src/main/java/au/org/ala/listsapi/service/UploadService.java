package au.org.ala.listsapi.service;

import au.org.ala.listsapi.controller.AuthUtils;
import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.ws.security.profile.AlaUserProfile;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

@Service
public class UploadService {

  private static final Logger logger = LoggerFactory.getLogger(UploadService.class);
  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
  @Autowired protected TaxonService taxonService;
  @Autowired protected ReleaseService releaseService;

  @Value("${temp.dir:/tmp}")
  private String tempDir;

  public boolean deleteList(String speciesListID, AlaUserProfile userProfile) {

    Optional<SpeciesList> list = speciesListMongoRepository.findById(speciesListID);
    if (list.isPresent() && AuthUtils.isAuthorized(list.get(), userProfile)) {
      logger.info("Deleting speciesListID " + speciesListID);
      speciesListIndexElasticRepository.deleteSpeciesListItemBySpeciesListID(speciesListID);
      speciesListItemMongoRepository.deleteBySpeciesListID(speciesListID);
      speciesListMongoRepository.deleteById(speciesListID);
      logger.info("Deleted speciesListID " + speciesListID);
      return true;
    } else {
      return false;
    }
  }

  public SpeciesList ingest(
      String userId, SpeciesList speciesListMetadata, File fileToLoad, boolean dryRun)
      throws Exception {

    // create the species list in mongo
    SpeciesList speciesList = new SpeciesList();
    speciesList.setOwner(userId);
    extractUpdates(speciesListMetadata, speciesList);
    speciesList = speciesListMongoRepository.save(speciesList);

    IngestJob ingestJob = ingest(speciesList.getId(), fileToLoad, dryRun, false);

    speciesList.setFieldList(ingestJob.getFieldList());
    speciesList.setFacetList(ingestJob.getFacetList());
    speciesList.setRowCount(ingestJob.getRowCount());
    speciesList.setOriginalFieldList(ingestJob.getOriginalFieldNames());

    speciesList = speciesListMongoRepository.save(speciesList);

    releaseService.release(speciesList.getId());
    return speciesList;
  }

  private void extractUpdates(SpeciesList speciesListMetadata, SpeciesList speciesList) {
    speciesList.setTitle(speciesListMetadata.getTitle());
    speciesList.setDescription(speciesListMetadata.getDescription());
    speciesList.setListType(speciesListMetadata.getListType());
    speciesList.setAuthority(speciesListMetadata.getAuthority());
    speciesList.setRegion(speciesListMetadata.getRegion());
    speciesList.setLicence(speciesListMetadata.getLicence());
    speciesList.setIsPrivate(speciesListMetadata.getIsPrivate());
    speciesList.setIsAuthoritative(speciesListMetadata.getIsAuthoritative());
    speciesList.setIsInvasive(speciesListMetadata.getIsInvasive());
    speciesList.setIsThreatened(speciesListMetadata.getIsThreatened());
  }

  public SpeciesList updateMetadata(SpeciesList speciesList) {
    if (speciesList.getId() != null) {
      Optional<SpeciesList> speciesList1 = speciesListMongoRepository.findById(speciesList.getId());
      if (speciesList1.isPresent()) {
        extractUpdates(speciesList, speciesList1.get());
        return speciesListMongoRepository.save(speciesList1.get());
      } else {
        return null;
      }
    }
    return null;
  }

  public File getFileTemp(MultipartFile file) {
    return new File(
        tempDir
            + "/upload-"
            + System.currentTimeMillis()
            + "-"
            + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_"));
  }

  public SpeciesList reload(String speciesListID, File fileToLoad, boolean dryRun)
      throws Exception {
    if (speciesListID != null) {

      Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListID);
      if (speciesList.isPresent()) {
        // delete from index
        speciesListIndexElasticRepository.deleteSpeciesListItemBySpeciesListID(speciesListID);
        // delete from mongo
        speciesListItemMongoRepository.deleteBySpeciesListID(speciesListID);

        IngestJob ingestJob = ingest(speciesListID, fileToLoad, dryRun, false);

        speciesList.get().setFieldList(ingestJob.getFieldList());
        speciesList.get().setFacetList(ingestJob.getFacetList());
        speciesList.get().setRowCount(ingestJob.getRowCount());
        speciesList.get().setOriginalFieldList(ingestJob.getOriginalFieldNames());
        SpeciesList speciesList1 = speciesListMongoRepository.save(speciesList.get());
        releaseService.release(speciesList1.getId());
        return speciesList1;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public IngestJob ingest(File fileToLoad, boolean dryRun, boolean skipIndexing) throws Exception {
    return ingest(null, fileToLoad, dryRun, skipIndexing);
  }

  public IngestJob ingest(
      String speciesListID, File fileToLoad, boolean dryRun, boolean skipIndexing)
      throws Exception {

    IngestJob ingestJob = null;

    Path path = fileToLoad.toPath();
    String mimeType = Files.probeContentType(path);

    // handle CSV
    if (mimeType.equals("text/csv")) {
      // load a CSV
      ingestJob = ingestCSV(speciesListID, fileToLoad, dryRun, skipIndexing);
    }

    // handle zip file
    if (mimeType.equals("application/zip")) {
      try (ZipFile zipFile = new ZipFile(fileToLoad)) {
        // load a zip file
        ingestJob = ingestZip(speciesListID, zipFile, dryRun, skipIndexing);
      }
    }

    if (ingestJob != null) {
      ingestJob.setLocalFile(fileToLoad.getName());
      return ingestJob;
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
        IngestJob ingestJob =
            loadCSV(speciesListID, zipFile.getInputStream(entry), dryRun, skipIndexing, false);
        return ingestJob;
      }
    }
    return null;
  }

  public IngestJob loadCSV(
      String speciesListID, InputStream inputStream, boolean dryRun, boolean skipIndexing, boolean isMigration)
      throws Exception {

    if (!dryRun && speciesListID != null) {
      Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListID);
      if (speciesList.isEmpty()) {
        throw new Exception("Species list not found");
      }
    }

    int rowCount = 0;
    CsvMapper mapper = new CsvMapper();
    CsvSchema schema = CsvSchema.emptySchema().withHeader();
    MappingIterator<Map<String, String>> iterator =
        mapper.reader(Map.class).with(schema).readValues(inputStream);

    // store
    Map<String, Set<String>> facets = new HashMap<>();
    Set<String> notFacetable = new HashSet<>();
    Set<String> fieldNames = new HashSet<>();

    int recordsWithoutScientificName = 0;

    List<String> originalFieldNames = new ArrayList<>();

    while (iterator.hasNext()) {
      Map<String, String> values = iterator.next();

      if (isMigration) {
        // remove legacy data
        values.remove("guid");
        values.remove("scientificName");
        values.remove("family");
        values.remove("kingdom");
      }

      if (originalFieldNames.isEmpty()){
        originalFieldNames.addAll(values.keySet());
      }

      String scientificName = values.remove(DwcTerm.scientificName.simpleName());
      String taxonID = values.remove(DwcTerm.taxonID.simpleName());
      String taxonConceptID = values.remove(DwcTerm.taxonConceptID.simpleName());

      String suppliedName = values.remove("Supplied Name");

      if (suppliedName != null) {
        scientificName = suppliedName;
      }

      if (StringUtils.isEmpty(scientificName)
          && StringUtils.isEmpty(taxonID)
          && StringUtils.isEmpty(taxonConceptID)) {
        recordsWithoutScientificName++;
      }

      String vernacularName = values.remove(DwcTerm.vernacularName.simpleName());
      String kingdom = values.remove(DwcTerm.kingdom.simpleName());
      String phylum = values.remove(DwcTerm.phylum.simpleName());
      String classs = values.remove(DwcTerm.class_.simpleName());
      String order = values.remove(DwcTerm.order.simpleName());
      String family = values.remove(DwcTerm.family.simpleName());
      String genus = values.remove(DwcTerm.genus.simpleName());

      // lookup the scientific name
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
                    Set<String> distinctValues =
                        facets.getOrDefault(cleanKey(e.getKey()), new HashSet<>());
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
        SpeciesListItem speciesListItem =
            new SpeciesListItem(
                null,
                speciesListID,
                taxonID,
                scientificName,
                vernacularName,
                kingdom,
                phylum,
                classs,
                order,
                family,
                genus,
                keyValues,
                null);

        speciesListItemMongoRepository.save(speciesListItem);
      }
      rowCount++;
    }

    IngestJob ingestJob = new IngestJob();
    logger.info("Field names = " + StringUtils.join(fieldNames, ", "));
    List<String> facetNames = new ArrayList<>();
    facetNames.addAll(fieldNames);
    facetNames.removeAll(notFacetable);

    logger.info("Facet-able names = " + StringUtils.join(facetNames, ", "));
    ingestJob.setFieldList(fieldNames.stream().toList());
    ingestJob.setFacetList(facetNames);
    ingestJob.setRowCount(rowCount);
    ingestJob.setOriginalFieldNames(originalFieldNames);

    if (recordsWithoutScientificName > 0) {
      List<String> validationError = new ArrayList<>();
      if (recordsWithoutScientificName == rowCount) {
        validationError.add("ALL_RECORDS_WITHOUT_SCIENTIFIC_NAME");
      } else {
        validationError.add("SOME_RECORDS_WITHOUT_SCIENTIFIC_NAME");
      }
      ingestJob.setValidationErrors(validationError);
    }

    if (!dryRun && !skipIndexing) {
      taxonService.taxonMatchDataset(speciesListID);
      taxonService.reindex(speciesListID);
    }

    return ingestJob;
  }

  public static String cleanKey(String keyName) {
    try {
      String cleanedName =
          keyName
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
