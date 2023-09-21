package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.Release;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.ReleaseMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ReleaseService {

  private static final Logger logger = LoggerFactory.getLogger(ReleaseService.class);

  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
  @Autowired protected SpeciesListMongoRepository speciesItemMongoRepository;

  @Autowired protected ReleaseMongoRepository releaseMongoRepository;

  @Value("${release.s3.enabled:false}")
  private Boolean s3Enabled;

  @Value("${release.s3.bucket}")
  private String s3Bucket;

  @Value("${release.directory:/tmp/}")
  private String releaseDirectory;

  public Release release(String speciesListID) throws Exception {
    return release(speciesListID, false);
  }

  /**
   * Write the speciesList to file and upload to S3
   *
   * @param speciesListID
   */
  public Release release(String speciesListID, boolean ignoreCurrentVersion) throws Exception {

    logger.info("Releasing " + speciesListID);
    int size = 10;
    int page = 0;
    boolean done = false;

    Optional<SpeciesList> speciesList = speciesItemMongoRepository.findById(speciesListID);
    if (speciesList.isEmpty()) {
      throw new Exception("No list for this ID");
    }

    // do we need to release the list ? check version numbers of lists....
    Release lastRelease = getLastRelease(speciesListID);
    if (!ignoreCurrentVersion
        && lastRelease != null
        && lastRelease.getReleasedVersion() == speciesList.get().getVersion()) {
      // dont re-release, there are no changes...
      logger.info("Not re-releasing, there are no changes for " + speciesListID);
      return lastRelease;
    }

    final List<String> fieldList = speciesList.get().getFieldList();
    final CsvMapper csvMapper = new CsvMapper();

    String storedLocation =
        releaseDirectory + "/" + speciesListID + "-" + (speciesList.get().getVersion()) + ".csv";
    File csvFile = new File(storedLocation);

    try (FileWriter fileWriter = new FileWriter(csvFile)) {

      SequenceWriter seqW = csvMapper.writer().writeValues(fileWriter);
      // construct headers
      String[] combinedHdrs = generateHeaders(fieldList);
      seqW.write(combinedHdrs);

      while (!done) {
        Pageable paging = PageRequest.of(page, size);
        Page<SpeciesListItem> speciesListItems =
            speciesListItemMongoRepository.findBySpeciesListID(speciesListID, paging);
        if (!speciesListItems.getContent().isEmpty()) {
          speciesListItems.forEach(
              speciesListItem -> {
                writeSpeciesItem(fieldList, seqW, speciesListItem);
              });
        } else {
          done = true;
        }
        page++;
      }
      seqW.flush();
      seqW.close();
    }

    // release repo
    Release release = new Release();
    release.setSpeciesListID(speciesListID);
    release.setReleasedVersion(speciesList.get().getVersion());
    release.setStoredLocation(storedLocation);
    release.setMetadata(speciesList.get());
    releaseMongoRepository.save(release);
    logger.info("Released " + speciesListID);
    return release;
  }

  private Release getLastRelease(String speciesListID) {
    Pageable paging = PageRequest.of(0, 1, Sort.Direction.DESC, "version");
    Page<Release> release = releaseMongoRepository.findBySpeciesListID(speciesListID, paging);
    if (!release.getContent().isEmpty()) {
      return release.getContent().get(0);
    }
    return null;
  }

  private void writeSpeciesItem(
      List<String> fieldList, SequenceWriter seqW, SpeciesListItem speciesListItem) {
    try {

      Map<String, String> map = new HashMap<>();
      speciesListItem.getProperties().forEach(kv -> map.put(kv.getKey(), kv.getValue()));

      // write the data to Elasticsearch
      String[] originalClassification = {
        speciesListItem.getId() != null ? speciesListItem.getId() : "",
        speciesListItem.getScientificName() != null ? speciesListItem.getScientificName() : "",
        speciesListItem.getTaxonID() != null ? speciesListItem.getTaxonID() : "",
        speciesListItem.getKingdom() != null ? speciesListItem.getKingdom() : "",
        speciesListItem.getPhylum() != null ? speciesListItem.getPhylum() : "",
        speciesListItem.getClasss() != null ? speciesListItem.getClasss() : "",
        speciesListItem.getOrder() != null ? speciesListItem.getOrder() : "",
        speciesListItem.getFamily() != null ? speciesListItem.getFamily() : "",
        speciesListItem.getGenus() != null ? speciesListItem.getGenus() : "",
      };

      List<String> fields = new ArrayList<>();
      for (String field : fieldList) {
        fields.add(map.getOrDefault(field, ""));
      }

      String[] combined = ArrayUtils.addAll(originalClassification, fields.toArray(new String[0]));
      seqW.write(combined);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private String[] generateHeaders(List<String> fieldList) {
    String[] classficationHdrs =
        new String[] {
          "id",
          DwcTerm.scientificName.simpleName(),
          DwcTerm.taxonID.simpleName(),
          DwcTerm.kingdom.simpleName(),
          DwcTerm.phylum.simpleName(),
          DwcTerm.class_.simpleName(),
          DwcTerm.order.simpleName(),
          DwcTerm.family.simpleName(),
          DwcTerm.genus.simpleName(),
        };

    String[] combinedHdrs = ArrayUtils.addAll(classficationHdrs, fieldList.toArray(new String[0]));
    return combinedHdrs;
  }
}
