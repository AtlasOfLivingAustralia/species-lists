package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.json.Json;
import org.elasticsearch.action.search.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.BulkFailureException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.client.erhlc.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchScrollHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TaxonService {

  @Value("${namematching.url}")
  private String namematchingQueryUrl;

  private static final Logger logger = LoggerFactory.getLogger(TaxonService.class);
  public static final String SPECIES_LIST_ID = "speciesListID";
  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
  @Autowired protected ElasticsearchOperations elasticsearchOperations;
  @Autowired protected MongoTemplate mongoTemplate;

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

  private void bulkIndexSafe(List<IndexQuery> updateList, SpeciesList list) {
    try {
      elasticsearchOperations.bulkIndex(updateList, SpeciesListIndex.class);
    } catch (BulkFailureException e) {
      logger.error(e.getMessage(), e);

      Set<String> failedIds = e.getFailedDocuments().keySet();
      logger.error(" -- FAILED IDS --");
      logger.error(failedIds.toString(), e);

      try {
        Optional<IndexQuery> failedItem = updateList.stream().filter(item -> failedIds.contains(item.getId())).findFirst();
        ObjectMapper mapper = new ObjectMapper();

        logger.error(" -- FAILED DOCUMENT EXAMPLE --");
        logger.error(mapper.writeValueAsString(failedItem.get()));
        logger.error(" -- FAILED DOCUMENTS --");
      } catch (JsonProcessingException ex) {
        logger.error("Failed to write update list to console", ex);
      }
    }
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
                  bulkIndexSafe(updateList, speciesList);
                  updateList.clear();
                }
              } catch (Exception e) {
                logger.error(e.getMessage(), e);
              }
            });
        if (!updateList.isEmpty()) {
          bulkIndexSafe(updateList, speciesList);
          updateList.clear();
        }
      } else {
        done = true;
      }
      page++;
    }


    logger.info("Indexing " + speciesListID + " complete.");
  }

  private void resetMatchCheckForDataset(String speciesListID) {
    var query = new Query(Criteria.where("speciesListID").is(speciesListID));
    Update update = new Update();

    update.set("matchChecked", false);
    mongoTemplate.updateMulti(query, update, SpeciesListItem.class);
  }

  public long taxonMatchDataset(String speciesListID) {
    logger.info("Taxon matching " + speciesListID);

    resetMatchCheckForDataset(speciesListID);

    Optional<SpeciesList> optionalSp = speciesListMongoRepository.findById(speciesListID);
    if (!optionalSp.isPresent()) return 0;

    int size = 100;
    int page = 0;
    long distinct = 0;
    boolean done = false;

    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      Page<SpeciesListItem> speciesListItems =
          speciesListItemMongoRepository.findBySpeciesListID(speciesListID, paging);

      if (!speciesListItems.getContent().isEmpty()) {
        try {
          List<SpeciesListItem> items = speciesListItems.getContent();
          updateClassifications(items);

          distinct += items.stream()
                  .filter(speciesListItem -> speciesListItem.getClassification().getSuccess())
                  .map(speciesListItem -> speciesListItem.getClassification().getTaxonConceptID()).distinct().count();

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

    return distinct;
  }

  public List<SpeciesListItem> updateClassifications(List<SpeciesListItem> speciesListItems) {
    try {
      List<Classification> classification = lookupTaxa(speciesListItems);
      for (int i = 0; i < speciesListItems.size(); i++) {
        speciesListItems.get(i).setClassification(classification.get(i));
        speciesListItems.get(i).setMatchChecked(true);
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

  public long getDistinctTaxaCount(String speciesListID) {
    logger.info("Getting distinct taxonConceptID count for speciesListID: " + speciesListID);
    try {
      // Use NativeQueryBuilder to construct the query
      NativeQueryBuilder queryBuilder = NativeQuery.builder();

      // Add a filter to match the provided speciesListID
      queryBuilder.withQuery(q ->
              q.bool(b ->
                      b.filter(f ->
                              f.term(t ->
                                      t.field(SPECIES_LIST_ID + ".keyword").value(speciesListID)
                              )
                      )
              )
      );

      // Add a cardinality aggregation for distinct taxonConceptID
      queryBuilder.withAggregation(
              "distinctTaxonConceptID",
              Aggregation.of(a -> a.cardinality(c -> c.field("classification.taxonConceptID.keyword")))
      );

      // Build the query
      NativeQuery nativeQuery = queryBuilder.build();

      // Execute the query using ElasticsearchOperations
      SearchHits<SpeciesListIndex> searchHits =
              elasticsearchOperations.search(nativeQuery, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

      // Extract the aggregation result
      ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
      ElasticsearchAggregation cardinalityAgg =
              aggregations.aggregations().stream()
                      .filter(agg -> agg.aggregation().getName().equals("distinctTaxonConceptID"))
                      .findFirst()
                      .orElse(null);

      if (cardinalityAgg != null) {
        return cardinalityAgg.aggregation().getAggregate().cardinality().value();
      }

    } catch (Exception e) {
      logger.error("Error fetching distinct taxonConceptID count for speciesListID: " + speciesListID, e);
    }

    return 0L; // Return 0 if an error occurs
  }

}
