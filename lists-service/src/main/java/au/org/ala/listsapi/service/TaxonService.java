package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TaxonService {

  @Value("${namematching.url}")
  private String namematchingQueryUrl;

  private static final Logger logger = LoggerFactory.getLogger(TaxonService.class);
  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
  @Autowired protected ElasticsearchOperations elasticsearchOperations;

  @Async("processExecutor")
  public void reindex() {
    logger.info("Indexing all datasets");
    int size = 1000;
    int page = 0;
    boolean done = false;
    AtomicInteger datasetsIndex = new AtomicInteger();
    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      Page<SpeciesList> speciesLists = speciesListMongoRepository.findAll(paging);
      if (speciesLists.getContent().size() > 0) {
        speciesLists.forEach(
            speciesList -> {
              try {
                reindex(speciesList.getId());
                datasetsIndex.getAndIncrement();
              } catch (Exception e) {
                logger.error(e.getMessage(), e);
              }
            });
      } else {
        done = true;
      }
      page++;
    }
    logger.info("Indexing of all datasets complete. " + datasetsIndex + " datasets indexed.");
  }

  @Async("processExecutor")
  public void taxonMatchDatasets() {
    logger.info("Taxon matching all datasets");
    int size = 10;
    int page = 0;
    boolean done = false;
    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      Page<SpeciesList> speciesLists = speciesListMongoRepository.findAll(paging);
      if (!speciesLists.getContent().isEmpty()) {
        speciesLists.forEach(
            speciesList -> {
              try {
                taxonMatchDataset(speciesList.getId());
              } catch (Exception e) {
                logger.error(e.getMessage(), e);
              }
            });
      } else {
        done = true;
      }
      page++;
    }
    logger.info("Taxon matching all datasets complete.");
  }

  public void reindex(String speciesListID) {

    logger.info("Indexing " + speciesListID);
    SpeciesList speciesList = speciesListMongoRepository.findById(speciesListID).get();
    int size = 1000;
    int page = 0;
    boolean done = false;

    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      List<IndexQuery> updateList = new ArrayList<>();
      Page<SpeciesListItem> speciesListItems =
          speciesListItemMongoRepository.findBySpeciesListID(speciesListID, paging);

      if (!speciesListItems.getContent().isEmpty()) {
        speciesListItems.forEach(
            speciesListItem -> {
              try {

                Map<String, String> map = new HashMap<>();
                speciesListItem.getProperties().forEach(kv -> map.put(kv.getKey(), kv.getValue()));

                // write the data to Elasticsearch
                SpeciesListIndex speciesListIndex =
                    new SpeciesListIndex(
                        speciesListItem.getId(),
                        speciesList.getDataResourceUid(),
                        speciesList.getTitle(),
                        speciesList.getListType(),
                        speciesListItem.getSpeciesListID(),
                        speciesListItem.getScientificName(),
                        speciesListItem.getVernacularName(),
                        speciesListItem.getTaxonID(),
                        speciesListItem.getKingdom(),
                        speciesListItem.getPhylum(),
                        speciesListItem.getClasss(),
                        speciesListItem.getOrder(),
                        speciesListItem.getFamily(),
                        speciesListItem.getGenus(),
                        map,
                        speciesListItem.getClassification(),
                        speciesList.getIsPrivate() != null ? speciesList.getIsPrivate() : false,
                        speciesList.getIsAuthoritative() != null
                            ? speciesList.getIsAuthoritative()
                            : false,
                        speciesList.getIsBIE() != null ? speciesList.getIsBIE() : false,
                        speciesList.getIsSDS() != null ? speciesList.getIsSDS() : false,
                        speciesList.getRegion() != null || speciesList.getWkt() != null,
                        speciesList.getOwner(),
                        speciesList.getEditors(),
                        speciesList.getTags() != null ? speciesList.getTags() : new ArrayList<>(),
                        speciesList.getDateCreated() != null ? speciesList.getDateCreated().toString(): null,
                        speciesList.getLastUpdated() != null ? speciesList.getLastUpdated().toString(): null,
                        speciesList.getLastUpdatedBy()
                );

                IndexQuery indexQuery =
                    new IndexQueryBuilder()
                        .withId(speciesListItem.getId())
                        .withObject(speciesListIndex)
                        .build();

                updateList.add(indexQuery);

                if (updateList.size() == 1000) {
                  elasticsearchOperations.bulkIndex(updateList, SpeciesListIndex.class);
                  updateList.clear();
                }
              } catch (Exception e) {
                logger.error(e.getMessage(), e);
              }
            });
        if (!updateList.isEmpty()) {
          elasticsearchOperations.bulkIndex(updateList, SpeciesListIndex.class);
          updateList.clear();
        }
      } else {
        done = true;
      }
      page++;
    }
    logger.info("Indexing " + speciesListID + " complete.");
  }

  public void taxonMatchDataset(String speciesListID) {

    logger.info("Taxon matching " + speciesListID);
    Optional<SpeciesList> optionalSp = speciesListMongoRepository.findById(speciesListID);
    if (!optionalSp.isPresent()) return;

    int size = 100;
    int page = 0;
    boolean done = false;
    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      Page<SpeciesListItem> speciesListItems =
          speciesListItemMongoRepository.findBySpeciesListID(speciesListID, paging);

      if (!speciesListItems.getContent().isEmpty()) {
        try {
          List<SpeciesListItem> items = speciesListItems.getContent();
          updateClassifications(items);
          speciesListItemMongoRepository.saveAll(items);

        } catch (Exception e) {
          logger.error(e.getMessage(), e);
        }
      } else {
        done = true;
      }
      page++;
    }
    logger.info("Taxon matching " + speciesListID + " complete.");
  }

  public List<SpeciesListItem> updateClassifications(List<SpeciesListItem> speciesListItems) {
    try {
      List<Classification> classification = lookupTaxa(speciesListItems);
      for (int i = 0; i < speciesListItems.size(); i++) {
        speciesListItems.get(i).setClassification(classification.get(i));
      }
      // write to mongo
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    return speciesListItems;
  }

  public Classification lookupTaxon(SpeciesListItem item) throws Exception {
    return lookupTaxa(List.of(item)).get(0);
  }

  public List<Classification> lookupTaxa(List<SpeciesListItem> items) throws Exception {

    List<Map<String, String>> values =
        items.stream().map(item -> item.toTaxonMap()).collect(Collectors.toList());
    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(values);
    HttpRequest httpRequest =
        HttpRequest.newBuilder(new URI(namematchingQueryUrl + "/api/searchAllByClassification"))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .header("Content-Type", "application/json")
            .build();

    HttpResponse<String> response =
        HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .build()
            .send(httpRequest, HttpResponse.BodyHandlers.ofString());

    ObjectMapper objectMapper = new ObjectMapper();

    if (response.statusCode() == 200) {
      List<Classification> classifications =
          objectMapper.readValue(response.body(), new TypeReference<List<Classification>>() {});
      return classifications;
    }
    return null;
  }
}
