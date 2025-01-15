package au.org.ala.listsapi.service;

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
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
  @Autowired protected MetadataService metadataService;
  @Autowired protected AuthUtils authUtils;

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

  public boolean deleteList(String speciesListID, AlaUserProfile userProfile) {

    Optional<SpeciesList> list = speciesListMongoRepository.findById(speciesListID);
    if (list.isPresent() && authUtils.isAuthorized(list.get(), userProfile)) {
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
      AlaUserProfile user, InputSpeciesList speciesListMetadata, File fileToLoad, boolean dryRun)
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

    IngestJob ingestJob = ingest(speciesList.getId(), fileToLoad, dryRun, false);

    speciesList.setFacetList(ingestJob.getFacetList());
    speciesList.setFieldList(ingestJob.getFieldList());
    speciesList.setOriginalFieldList(ingestJob.getOriginalFieldNames());
    speciesList.setRowCount(ingestJob.getRowCount());
    speciesList.setDistinctMatchCount(ingestJob.getDistinctMatchCount());

    speciesList = speciesListMongoRepository.save(speciesList);

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
    speciesList.setLicence(speciesListMetadata.getLicence());
    speciesList.setListType(speciesListMetadata.getListType());
    speciesList.setRegion(speciesListMetadata.getRegion());
    speciesList.setTitle(speciesListMetadata.getTitle());
    speciesList.setTags(speciesListMetadata.getTags());
  }

  public File getFileTemp(MultipartFile file) {
    return new File(
        tempDir
            + "/upload-"
            + System.currentTimeMillis()
            + "-"
            + Objects.requireNonNull(file.getOriginalFilename()).replaceAll("[^a-zA-Z0-9._-]", "_"));
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
        // releaseService.release(speciesList1.getId());
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
    List<SpeciesListItem> batch = new ArrayList<>();

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
                null,
                speciesListID,
                cleanField(taxonID),
                cleanField(scientificName),
                cleanField(vernacularName),
                cleanField(kingdom),
                cleanField(phylum),
                cleanField(classs),
                cleanField(order),
                cleanField(family),
                cleanField(genus),
                keyValues,
                null, //classification
                new Date(), // dateCreated
                new Date(), // lastUpdated,
                null
            );

        batch.add(speciesListItem);

        if (batch.size() == 1000) {
          speciesListItemMongoRepository.saveAll(batch);
          batch.clear();
        }
      }
      rowCount++;
    }

    if (!batch.isEmpty()) {
      speciesListItemMongoRepository.saveAll(batch);
      batch.clear();
    }
    logger.info("Species list loaded into database");

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
