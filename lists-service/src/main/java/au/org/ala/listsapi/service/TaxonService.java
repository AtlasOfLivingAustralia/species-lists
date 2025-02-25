package au.org.ala.listsapi.service;

import au.org.ala.listsapi.controller.MongoUtils;
import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
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

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.BulkFailureException;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
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
  @Autowired protected ProgressService progressService;
  @Autowired protected MongoUtils mongoUtils;


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
                long distinctMatchCount = taxonMatchDataset(speciesList.getId());
                speciesList.setDistinctMatchCount(distinctMatchCount);
                speciesListMongoRepository.save(speciesList);
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
    long startTime = System.nanoTime();
    logger.info("[{}|reindex|bulkIndex] Indexing {} items", list.getId(), updateList.size());
    try {
      elasticsearchOperations.bulkIndex(updateList, SpeciesListIndex.class);
      progressService.addIngestElasticProgress(list.getId(), updateList.size());
    } catch (BulkFailureException e) {
      logger.error("[{}|reindex|bulkIndex] Indexing error: {}", list.getId(), e.getMessage());

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
    long elapsed = System.nanoTime() - startTime;
    logger.info("[{}|reindex|bulkIndex] Indexing " + updateList.size() + " items took " + (elapsed / 1000000) + "ms", list.getId());
  }

  private SpeciesListIndex listItemToIndex(SpeciesList speciesList, SpeciesListItem speciesListItem) {
    Map<String, String> map = new HashMap<>();
    speciesListItem.getProperties().forEach(kv -> map.put(kv.getKey(), kv.getValue()));

    // write the data to Elasticsearch
    return new SpeciesListIndex(
                    speciesListItem.getId().toString(),
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
  }

  public void reindex(String speciesListID) {
    logger.info("[{}|reindex] Starting indexing", speciesListID);
    long findByIdStart = System.nanoTime();
    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
    long findByIdElapsed = (System.nanoTime() - findByIdStart) / 1000000;
    logger.info("[{},reindex] Reindex find by ID {}ms", speciesListID, findByIdElapsed);

    if (optionalSpeciesList.isEmpty()) return;

    SpeciesList speciesList = optionalSpeciesList.get();

    int batchSize = 1000;
    ObjectId lastId = null;

    boolean finished = false;
    while (!finished) {
      List<SpeciesListItem> speciesListItems =
          speciesListItemMongoRepository.findNextBatch(speciesList.getId(), lastId, PageRequest.of(0, batchSize));

      if (!speciesListItems.isEmpty()) {
        List<IndexQuery> updateList = new ArrayList<>();
        for (SpeciesListItem item : speciesListItems) {
          updateList.add(
                  new IndexQueryBuilder()
                  .withId(item.getId().toString())
                  .withObject(listItemToIndex(speciesList, item))
                  .build()
          );
        }

        try {
          bulkIndexSafe(updateList, speciesList);
          updateList.clear();
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
        }

        lastId = speciesListItems.get(speciesListItems.size() - 1).getId();
      } else {
        finished = true;
      }
    }


    logger.info("[{}|reindex] Indexing complete.", speciesListID);
  }

  public long taxonMatchDataset(String speciesListID) {
    logger.info("[{}|taxonMatch] Starting taxon matching", speciesListID);

    long findByIdStart = System.nanoTime();
    Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
    long findByIdElapsed = (System.nanoTime() - findByIdStart) / 1000000;
    logger.info("[{}|taxonMatch] Taxon match find by ID OR UID {}ms", speciesListID, findByIdElapsed);

    if (optionalSpeciesList.isEmpty()) return 0;
    SpeciesList speciesList = optionalSpeciesList.get();

    // Reset ingestion progress
    long resetProgressStart = System.nanoTime();
    progressService.resetIngestProgress(speciesList.getId());
    long resetProgressElapsed = (System.nanoTime() - resetProgressStart) / 1000000;
    logger.info("[{}|taxonMatch] Reset ingest progress {}ms", speciesListID, resetProgressElapsed);

    logger.info("[{}|taxonMatch] Started taxon matching", speciesListID);

    int batchSize = 1000;
    ObjectId lastId = null;

    Set<String> distinctTaxa = new HashSet<>();

    boolean finished = false;
    while (!finished) {
      long startTime = System.nanoTime();

      List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(speciesList.getId(), lastId, PageRequest.of(0, batchSize));
      long elapsed = System.nanoTime() - startTime;

      logger.info("[{}|taxonMatch] Fetched {} items in {} ms",
              speciesListID, items.size(), elapsed / 1_000_000);

      if (items.isEmpty()) {
        finished = true;
      } else {
        try {

          // Update classifications
          updateClassifications(items);

          // Save updated items
          long saveClassStart = System.nanoTime();
          mongoUtils.speciesListItemsBulkUpdate(items, List.of("classification"));
          long saveClassElapsed = (System.nanoTime() - saveClassStart) / 1000000;
          logger.info("[{}|taxonMatch] Save updated classification took {}ms", speciesListID, saveClassElapsed);

          // Record progress
          long updatedProgressStart = System.nanoTime();
          progressService.addIngestMongoProgress(speciesList.getId(), items.size());
          long updateProgressElapsed = (System.nanoTime() - updatedProgressStart) / 1000000;
          logger.info("[{}|taxonMatch] Update mongo progress {}ms", speciesListID, updateProgressElapsed);

          items.forEach(speciesListItem -> distinctTaxa.add(speciesListItem.getClassification().getTaxonConceptID()));
          lastId = items.get(items.size() - 1).getId();

        } catch (Exception e) {
          logger.error(e.getMessage(), e);
        }
      }
    }

    logger.info("[{}|taxonMatch] Taxon matching complete. Found {} distinct taxa.",
            speciesListID, distinctTaxa.size());

    return distinctTaxa.size();
  }

  public void updateClassifications(List<SpeciesListItem> speciesListItems) {
    try {
      List<Classification> classification = lookupTaxa(speciesListItems);
      for (int i = 0; i < speciesListItems.size(); i++) {
        speciesListItems.get(i).setClassification(classification.get(i));
      }
      // write to mongo
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  public Classification lookupTaxon(SpeciesListItem item) throws Exception {
    return lookupTaxa(List.of(item)).get(0);
  }

  public List<Classification> lookupTaxa(List<SpeciesListItem> items) throws Exception {

    List<Map<String, String>> values =
        items.stream().map(SpeciesListItem::toTaxonMap).collect(Collectors.toList());
    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(values);
    HttpRequest httpRequest =
        HttpRequest.newBuilder(new URI(namematchingQueryUrl + "/api/searchAllByClassification"))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .header("Content-Type", "application/json")
            .build();


    long matchRequestStart = System.nanoTime();
    HttpResponse<String> response =
        HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .build()
            .send(httpRequest, HttpResponse.BodyHandlers.ofString());
    long matchRequestElapsed = (System.nanoTime() - matchRequestStart) / 1000000;
    logger.info("[{}|taxonMatch] Match request took {}ms", items.get(0).getSpeciesListID(), matchRequestElapsed);

    ObjectMapper objectMapper = new ObjectMapper();

    if (response.statusCode() == 200) {
      List<Classification> classifications =
          objectMapper.readValue(response.body(), new TypeReference<List<Classification>>() {});
      return classifications;
    } else {
      logger.error("Classification lookup failed " + items);
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
