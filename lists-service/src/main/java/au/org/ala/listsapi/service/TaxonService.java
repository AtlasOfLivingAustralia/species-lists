package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
  public void reindex(boolean rematchTaxonomy) {
    logger.info("Rematching all datasets");
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
                reindex(speciesList.getId(), rematchTaxonomy);
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
    logger.info("Rematching all datasets complete. " + datasetsIndex + " datasets indexed.");
  }

  @Async("processExecutor")
  public void taxonMatchDatasets(boolean rematchTaxonomy) {
    logger.info("Rematching all datasets");
    int size = 10;
    int page = 0;
    boolean done = false;
    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      Page<SpeciesList> speciesLists = speciesListMongoRepository.findAll(paging);
      if (speciesLists.getContent().size() > 0) {
        speciesLists.forEach(
            speciesList -> {
              try {
                taxonMatchDataset(speciesList.getId(), rematchTaxonomy);
              } catch (Exception e) {
                logger.error(e.getMessage(), e);
              }
            });
      } else {
        done = true;
      }
      page++;
    }
    logger.info("Rematching all datasets complete.");
  }

  @Async("processExecutor")
  public void reindex(String speciesListID, boolean rematchTaxonomy) {

    logger.info("Rematching " + speciesListID);
    SpeciesList speciesList = speciesListMongoRepository.findById(speciesListID).get();
    int size = 1000;
    int page = 0;
    boolean done = false;

    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      List<IndexQuery> updateList = new ArrayList<>();
      Page<SpeciesListItem> speciesListItems =
          speciesListItemMongoRepository.findBySpeciesListID(speciesListID, paging);

      if (speciesListItems.getContent().size() > 0) {
        speciesListItems.forEach(
            speciesListItem -> {
              try {
                if (rematchTaxonomy) {
                  updateClassification(speciesListItem);
                  speciesListItemMongoRepository.save(speciesListItem);
                }

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
                        speciesList.getOwner(),
                        speciesList.getEditors());

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
    logger.info("Rematching " + speciesListID + " complete.");
  }

  @Async("processExecutor")
  public void taxonMatchDataset(String speciesListID, boolean rematchTaxonomy) {

    logger.info("Rematching " + speciesListID);
    SpeciesList speciesList = speciesListMongoRepository.findById(speciesListID).get();
    int size = 10;
    int page = 0;
    boolean done = false;
    while (!done) {
      Pageable paging = PageRequest.of(page, size);

      Page<SpeciesListItem> speciesListItems =
          speciesListItemMongoRepository.findBySpeciesListID(speciesListID, paging);

      if (speciesListItems.getContent().size() > 0) {
        speciesListItems.forEach(
            speciesListItem -> {
              try {
                if (rematchTaxonomy) {
                  updateClassification(speciesListItem);
                  speciesListItemMongoRepository.save(speciesListItem);
                }

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
                        speciesList.getIsPrivate(),
                        speciesList.getOwner(),
                        speciesList.getEditors());
                logger.info("Reindexing - " + speciesListItem.getScientificName());
                speciesListIndexElasticRepository.save(speciesListIndex);

              } catch (Exception e) {
                logger.error(e.getMessage(), e);
              }
            });
      } else {
        done = true;
      }
      page++;
    }
    logger.info("Rematching " + speciesListID + " complete.");
  }

  public SpeciesListItem updateClassification(SpeciesListItem speciesListItem) {
    try {
      logger.info("Lookup for " + speciesListItem.getScientificName());
      Classification classification = lookupTaxonID(speciesListItem.getScientificName());
      // write to mongo
      speciesListItem.setClassification(classification);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    return speciesListItem;
  }

  private Classification lookupTaxonID(String scientificName) throws Exception {

    HttpRequest httpRequest =
        HttpRequest.newBuilder(
                new URI(
                    namematchingQueryUrl
                        + "/api/search?q="
                        + URLEncoder.encode(scientificName, "UTF-8")))
            .GET()
            .build();

    HttpResponse<String> response =
        HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .build()
            .send(httpRequest, HttpResponse.BodyHandlers.ofString());

    ObjectMapper objectMapper = new ObjectMapper();

    if (response.statusCode() == 200) {
      return objectMapper.readValue(response.body(), Classification.class);
    }
    return null;
  }
}
