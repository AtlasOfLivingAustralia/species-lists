package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemRepository;
import au.org.ala.listsapi.repo.SpeciesListRepository;
import au.org.ala.names.ws.api.NameMatchService;
import au.org.ala.names.ws.api.NameSearch;
import au.org.ala.names.ws.api.NameUsageMatch;
import au.org.ala.names.ws.client.ALANameUsageMatchServiceClient;
import au.org.ala.ws.ClientConfiguration;
import au.org.ala.ws.DataCacheConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TaxonService {

  private static final Logger logger = LoggerFactory.getLogger(TaxonService.class);
  public static final String SPECIES_LIST_ID = "speciesListID";

  @Value("${namematching.url:https://namematching-ws.ala.org.au}")
  private String nameMatchingServiceUrl;

  @Value("${namematching.bulkMatchBatchSize:250}")
  private int bulkMatchBatchSize;

  @Value("${namematching.datasetProcessingParallelism:5}")
  private int datasetProcessingParallelism;

  @Value("${namematching.threadPoolSize:10}")
  private int threadPoolSize;

  @Value("${namematching.maxConcurrentRequests:20}")
  private int maxConcurrentRequests;

  @Value("${namematching.dataCacheConfig.entryCapacity:400000}")
  private int cacheEntryCapacity;

  @Value("${namematching.dataCacheConfig.enableJmx:false}")
  private boolean cacheEnableJmx;

  @Value("${namematching.dataCacheConfig.eternal:true}")
  private boolean cacheEternal;

  @Value("${namematching.dataCacheConfig.keepDataAfterExpired:false}")
  private boolean cacheKeepDataAfterExpired;

  @Value("${namematching.dataCacheConfig.permitNullValues:false}")
  private boolean cachePermitNullValues;

  @Value("${namematching.dataCacheConfig.suppressExceptions:false}")
  private boolean cacheSuppressExceptions;

  @Autowired protected SpeciesListItemRepository speciesListItemRepository;

  @Autowired protected SpeciesListRepository speciesListRepository;

  @Autowired protected ProgressService progressService;

  @Autowired protected SearchHelperService searchHelperService;

  private NameMatchService nameMatchService;
  private ExecutorService executorService;
  private Semaphore rateLimiter;

  @PostConstruct
  public void init() {
    try {
      DataCacheConfiguration dataCacheConfig =
          DataCacheConfiguration.builder()
              .entryCapacity(cacheEntryCapacity)
              .enableJmx(cacheEnableJmx)
              .eternal(cacheEternal)
              .keepDataAfterExpired(cacheKeepDataAfterExpired)
              .permitNullValues(cachePermitNullValues)
              .suppressExceptions(cacheSuppressExceptions)
              .build();

      ClientConfiguration clientConfig =
          ClientConfiguration.builder()
              .baseUrl(new java.net.URL(nameMatchingServiceUrl))
              .dataCache(dataCacheConfig)
              .build();

      nameMatchService = new ALANameUsageMatchServiceClient(clientConfig);

      executorService = Executors.newFixedThreadPool(threadPoolSize);

      rateLimiter = new Semaphore(maxConcurrentRequests);

      logger.info(
          "TaxonService initialized with threadPoolSize={}, maxConcurrentRequests={}",
          threadPoolSize,
          maxConcurrentRequests);
    } catch (Exception e) {
      logger.error("Failed to initialize TaxonService", e);
      throw new RuntimeException("Failed to initialize TaxonService", e);
    }
  }

  @PreDestroy
  public void cleanup() {
    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  @Async("processExecutor")
  public void reindex() {
    logger.info("Indexing all datasets - No-op for Postgres migration");
    // In Postgres architecture, explicit reindexing to search index is not required
    // as the data is queried directly from the DB.
    // This method is kept to satisfy potential callers during transition.
  }

  @Async("processExecutor")
  public void taxonMatchDatasets() {
    long overallStartTime = System.nanoTime();
    logger.info("Taxon matching all datasets");

    progressService.setupMigrationProgress(speciesListRepository.count());

    List<SpeciesList> allLists = new ArrayList<>();
    int page = 0;
    int size = 1000;
    boolean done = false;

    logger.info("Fetching all species lists for sorting...");
    while (!done) {
      Pageable paging = PageRequest.of(page, size);
      Page<SpeciesList> speciesLists = speciesListRepository.findAll(paging);
      if (!speciesLists.getContent().isEmpty()) {
        allLists.addAll(speciesLists.getContent());
      } else {
        done = true;
      }
      page++;
    }

    allLists.sort(
        (a, b) -> {
          Integer countA = a.getRowCount();
          Integer countB = b.getRowCount();
          if (countA == null && countB == null) return 0;
          if (countA == null) return 1;
          if (countB == null) return -1;
          return countB.compareTo(countA);
        });

    logger.info("Processing {} species lists sorted by size (largest first)", allLists.size());

    AtomicInteger processedCount = new AtomicInteger(0);
    AtomicInteger totalLists = new AtomicInteger(allLists.size());

    ForkJoinPool customThreadPool = new ForkJoinPool(datasetProcessingParallelism);
    try {
      customThreadPool
          .submit(
              () ->
                  allLists
                      .parallelStream()
                      .forEach(
                          speciesList -> {
                            try {
                              long distinctMatchCount = taxonMatchDataset(speciesList.getId());
                              speciesList.setDistinctMatchCount(distinctMatchCount);
                              speciesListRepository.save(speciesList);
                              // reindex(speciesList.getId()); // Not needed for Postgres
                              int processed = processedCount.incrementAndGet();

                              if (processed % 10 == 0 || processed == totalLists.get()) {
                                progressService.updateMigrationProgress(speciesList);
                              }

                              logger.info(
                                  "Completed {}/{} lists. List {} had {} distinct taxa.",
                                  processed,
                                  totalLists.get(),
                                  speciesList.getId(),
                                  distinctMatchCount);

                            } catch (Exception e) {
                              logger.error("taxonMatchDatasets() error: {}", e.getMessage(), e);
                            }
                          }))
          .get();
    } catch (Exception e) {
      logger.error("Error in parallel dataset processing", e);
    } finally {
      customThreadPool.shutdown();
    }

    progressService.clearMigrationProgress();

    long overallElapsed = (System.nanoTime() - overallStartTime) / 1_000_000;
    long overallSeconds = overallElapsed / 1000;
    long minutes = overallSeconds / 60;
    long seconds = overallSeconds % 60;

    logger.info(
        "Taxon matching all {} datasets complete. Total time: {}m {}s ({} ms)",
        allLists.size(),
        minutes,
        seconds,
        overallElapsed);
  }

  public void reindex(String speciesListID) {
    logger.info("[{}|reindex] Reindexing not required for Postgres architecture", speciesListID);
  }

  public long taxonMatchDataset(String speciesListID) {
    logger.info("[{}|taxonMatch] Starting taxon matching", speciesListID);
    logMemoryUsage("Start of taxonMatchDataset");

    long findByIdStart = System.nanoTime();
    Optional<SpeciesList> optionalSpeciesList =
        speciesListRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
    long findByIdElapsed = (System.nanoTime() - findByIdStart) / 1000000;
    logger.info(
        "[{}|taxonMatch] Taxon match find by ID OR UID {}ms", speciesListID, findByIdElapsed);

    if (optionalSpeciesList.isEmpty()) return 0;
    SpeciesList speciesList = optionalSpeciesList.get();

    if (speciesList.getRowCount() != null && speciesList.getRowCount() == 0) {
      logger.info("[{}|taxonMatch] Skipping list - rowCount is null or 0", speciesListID);
      return 0;
    }

    long resetProgressStart = System.nanoTime();
    progressService.resetIngestProgress(speciesList.getId());
    long resetProgressElapsed = (System.nanoTime() - resetProgressStart) / 1000000;
    logger.info("[{}|taxonMatch] Reset ingest progress {}ms", speciesListID, resetProgressElapsed);
    logger.info("[{}|taxonMatch] Started taxon matching", speciesListID);

    String lastId = null; // Changed to String for Postgres/JPA
    Set<String> distinctTaxa = new HashSet<>();
    boolean finished = false;

    while (!finished) {
      long startTime = System.nanoTime();

      List<SpeciesListItem> items;
      // Use existing repo methods that support keyset pagination
      if (lastId == null) {
        items =
            speciesListItemRepository.findFirstBatch(
                speciesList.getId(), PageRequest.of(0, bulkMatchBatchSize));
      } else {
        items =
            speciesListItemRepository.findNextBatchAfter(
                speciesList.getId(), lastId, PageRequest.of(0, bulkMatchBatchSize));
      }

      long elapsed = System.nanoTime() - startTime;
      logger.info(
          "[{}|taxonMatch] Fetched {} items in {} ms",
          speciesListID,
          items.size(),
          elapsed / 1_000_000);

      if (items.isEmpty()) {
        finished = true;
      } else {
        try {
          updateClassifications(items, speciesList);

          long saveClassStart = System.nanoTime();
          // Using saveAll instead of custom update
          speciesListItemRepository.saveAll(items);
          long saveClassElapsed = (System.nanoTime() - saveClassStart) / 1000000;
          logger.info(
              "[{}|taxonMatch] Save updated classification took {}ms",
              speciesListID,
              saveClassElapsed);

          long updatedProgressStart = System.nanoTime();
          progressService.addIngestMongoProgress(speciesList.getId(), items.size());
          long updateProgressElapsed = (System.nanoTime() - updatedProgressStart) / 1000000;
          logger.info("[{}|taxonMatch] Update progress {}ms", speciesListID, updateProgressElapsed);

          items.forEach(
              speciesListItem -> {
                Classification classification = speciesListItem.getClassification();
                if (classification != null && classification.getTaxonConceptID() != null) {
                  distinctTaxa.add(classification.getTaxonConceptID());
                }
              });
          lastId = items.get(items.size() - 1).getId();

        } catch (Exception e) {
          logger.error("taxonMatchDataset() exception: {}", e.getMessage(), e);
        }
      }
    }

    logger.info(
        "[{}|taxonMatch] Taxon matching complete. Found {} distinct taxa.",
        speciesListID,
        distinctTaxa.size());

    return distinctTaxa.size();
  }

  public void updateClassifications(
      List<SpeciesListItem> speciesListItems, SpeciesList speciesList) {
    try {
      List<Classification> classifications = lookupTaxa(speciesListItems, speciesList);
      for (int i = 0; i < speciesListItems.size(); i++) {
        Classification classification = classifications.get(i);

        if (classification != null && !classification.getSuccess()) {
          classification.setMatchType("noMatch");
        }

        speciesListItems.get(i).setClassification(classification);
      }
    } catch (Exception e) {
      logger.error("updateClassifications() exception: {}", e.getMessage(), e);
    }
  }

  public Classification lookupTaxon(SpeciesListItem item) {
    try {
      Optional<SpeciesList> optionalSpeciesList =
          speciesListRepository.findById(item.getSpeciesListID());
      SpeciesList speciesList = optionalSpeciesList.orElse(null);

      return lookupTaxon(item, speciesList);
    } catch (Exception e) {
      logger.error("lookupTaxon() exception: {}", e.getMessage(), e);
      return null;
    }
  }

  public Classification lookupTaxon(SpeciesListItem item, SpeciesList speciesList) {
    try {
      List<Classification> results = lookupTaxa(List.of(item), speciesList);
      return results.isEmpty() ? null : results.get(0);
    } catch (Exception e) {
      logger.error("lookupTaxon() exception: {}", e.getMessage(), e);
      return null;
    }
  }

  public List<Classification> lookupTaxa(List<SpeciesListItem> items, SpeciesList speciesList) {
    if (items == null || items.isEmpty()) {
      return new ArrayList<>();
    }

    String speciesListID = items.get(0).getSpeciesListID();
    long startTime = System.nanoTime();

    try {
      List<NameSearch> nameSearches =
          items.stream()
              .map(item -> buildNameSearch(item, speciesList))
              .collect(Collectors.toList());

      logger.info("[{}|taxonMatch] Built {} name searches", speciesListID, nameSearches.size());

      if (nameSearches.size() <= bulkMatchBatchSize) {
        return performBulkMatch(nameSearches, speciesListID, startTime);
      }

      List<List<NameSearch>> batches = partitionList(nameSearches, bulkMatchBatchSize);
      logger.info(
          "[{}|taxonMatch] Split into {} batches of up to {} items",
          speciesListID,
          batches.size(),
          bulkMatchBatchSize);

      List<CompletableFuture<List<NameUsageMatch>>> futures = new ArrayList<>();

      for (int i = 0; i < batches.size(); i++) {
        final int batchIndex = i;
        final List<NameSearch> batch = batches.get(i);

        CompletableFuture<List<NameUsageMatch>> future =
            CompletableFuture.supplyAsync(
                () -> {
                  try {
                    long semaphoreWaitStart = System.nanoTime();
                    rateLimiter.acquire();
                    long semaphoreWaitTime = (System.nanoTime() - semaphoreWaitStart) / 1_000_000;

                    if (semaphoreWaitTime > 100) {
                      logger.warn(
                          "[{}|taxonMatch|batch-{}] Semaphore wait time: {}ms (bottleneck?)",
                          speciesListID,
                          batchIndex,
                          semaphoreWaitTime);
                    }

                    try {
                      long batchStart = System.nanoTime();
                      List<NameUsageMatch> results = nameMatchService.matchAll(batch);
                      long batchElapsed = (System.nanoTime() - batchStart) / 1_000_000;

                      if (batchElapsed > 5000) {
                        logger.warn(
                            "[{}|taxonMatch|batch-{}] SLOW BATCH: Matched {} items in {}ms (possible throttling or GC pause)",
                            speciesListID,
                            batchIndex,
                            batch.size(),
                            batchElapsed);
                      } else {
                        logger.info(
                            "[{}|taxonMatch|batch-{}] Matched {} items in {}ms",
                            speciesListID,
                            batchIndex,
                            batch.size(),
                            batchElapsed);
                      }

                      if (results == null || results.size() != batch.size()) {
                        logger.warn(
                            "[{}|taxonMatch|batch-{}] Result size mismatch. Expected {}, got {}",
                            speciesListID,
                            batchIndex,
                            batch.size(),
                            results == null ? 0 : results.size());
                        return fillMissingMatches(results, batch.size());
                      }

                      return results;
                    } finally {
                      rateLimiter.release();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error(
                        "[{}|taxonMatch|batch-{}] Thread interrupted",
                        speciesListID,
                        batchIndex,
                        e);
                    return createEmptyMatches(batch.size());
                  } catch (Exception e) {
                    logger.error(
                        "[{}|taxonMatch|batch-{}] Error matching batch: {}",
                        speciesListID,
                        batchIndex,
                        e.getMessage(),
                        e);
                    return createEmptyMatches(batch.size());
                  }
                },
                executorService);

        futures.add(future);
      }

      CompletableFuture<Void> allFutures =
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

      allFutures.get(5, TimeUnit.MINUTES);

      List<NameUsageMatch> allMatches =
          futures.stream()
              .map(CompletableFuture::join)
              .flatMap(List::stream)
              .collect(Collectors.toList());

      long elapsed = (System.nanoTime() - startTime) / 1_000_000;
      logger.info(
          "[{}|taxonMatch] Completed {} bulk lookups in {}ms ({} batches)",
          speciesListID,
          allMatches.size(),
          elapsed,
          batches.size());

      return convertToClassifications(allMatches);

    } catch (Exception e) {
      logger.error(
          "[{}|taxonMatch] Exception during bulk lookup: {}", speciesListID, e.getMessage(), e);
      return items.stream().map(item -> createEmptyClassification()).collect(Collectors.toList());
    }
  }

  private List<Classification> performBulkMatch(
      List<NameSearch> nameSearches, String speciesListID, long startTime) {
    try {
      rateLimiter.acquire();
      try {
        List<NameUsageMatch> matches = nameMatchService.matchAll(nameSearches);

        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        logger.info(
            "[{}|taxonMatch] Completed {} bulk lookups in {}ms (single batch)",
            speciesListID,
            matches.size(),
            elapsed);

        if (matches == null || matches.size() != nameSearches.size()) {
          logger.warn(
              "[{}|taxonMatch] Result size mismatch. Expected {}, got {}",
              speciesListID,
              nameSearches.size(),
              matches == null ? 0 : matches.size());
          matches = fillMissingMatches(matches, nameSearches.size());
        }

        return convertToClassifications(matches);
      } finally {
        rateLimiter.release();
      }
    } catch (Exception e) {
      logger.error(
          "[{}|taxonMatch] Exception during bulk lookup: {}", speciesListID, e.getMessage(), e);
      return nameSearches.stream()
          .map(ns -> createEmptyClassification())
          .collect(Collectors.toList());
    }
  }

  private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
      partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return partitions;
  }

  private List<NameUsageMatch> createEmptyMatches(int count) {
    List<NameUsageMatch> matches = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      matches.add(null);
    }
    return matches;
  }

  private List<NameUsageMatch> fillMissingMatches(List<NameUsageMatch> results, int expectedSize) {
    if (results == null) {
      return createEmptyMatches(expectedSize);
    }

    List<NameUsageMatch> filled = new ArrayList<>(results);
    while (filled.size() < expectedSize) {
      filled.add(null);
    }
    return filled;
  }

  private List<Classification> convertToClassifications(List<NameUsageMatch> matches) {
    return matches.stream().map(this::convertToClassification).collect(Collectors.toList());
  }

  private Classification convertToClassification(NameUsageMatch match) {
    if (match == null) {
      return createEmptyClassification();
    }

    Classification classification = new Classification();
    classification.setSuccess(match.isSuccess());
    classification.setTaxonConceptID(match.getTaxonConceptID());
    classification.setScientificName(match.getScientificName());
    classification.setScientificNameAuthorship(match.getScientificNameAuthorship());
    classification.setRank(match.getRank());
    classification.setRankID(match.getRankID());
    classification.setKingdom(match.getKingdom());
    classification.setKingdomID(match.getKingdomID());
    classification.setPhylum(match.getPhylum());
    classification.setPhylumID(match.getPhylumID());
    classification.setClasss(match.getClasss());
    classification.setClassID(match.getClassID());
    classification.setOrder(match.getOrder());
    classification.setOrderID(match.getOrderID());
    classification.setFamily(match.getFamily());
    classification.setFamilyID(match.getFamilyID());
    classification.setGenus(match.getGenus());
    classification.setGenusID(match.getGenusID());
    classification.setSpecies(match.getSpecies());
    classification.setSpeciesID(match.getSpeciesID());
    classification.setVernacularName(match.getVernacularName());
    classification.setMatchType(
        match.getMatchType() != null ? match.getMatchType().toString() : null);

    return classification;
  }

  private NameSearch buildNameSearch(SpeciesListItem item, SpeciesList speciesList) {
    NameSearch.NameSearchBuilder builder = NameSearch.builder();

    if (StringUtils.isNotBlank(item.getScientificName())) {
      builder.scientificName(StringUtils.trimToNull(item.getScientificName()));
    }
    if (StringUtils.isNotBlank(item.getTaxonID())) {
      builder.taxonID(StringUtils.trimToNull(item.getTaxonID()));
    }
    if (StringUtils.isNotBlank(item.getVernacularName())) {
      builder.vernacularName(StringUtils.trimToNull(item.getVernacularName()));
    }
    if (StringUtils.isNotBlank(item.getKingdom())) {
      builder.kingdom(StringUtils.trimToNull(item.getKingdom()));
    }
    if (StringUtils.isNotBlank(item.getPhylum())) {
      builder.phylum(StringUtils.trimToNull(item.getPhylum()));
    }
    if (StringUtils.isNotBlank(item.getClasss())) {
      builder.clazz(StringUtils.trimToNull(item.getClasss()));
    }
    if (StringUtils.isNotBlank(item.getOrder())) {
      builder.order(StringUtils.trimToNull(item.getOrder()));
    }
    if (StringUtils.isNotBlank(item.getFamily())) {
      builder.family(StringUtils.trimToNull(item.getFamily()));
    }
    if (StringUtils.isNotBlank(item.getGenus())) {
      builder.genus(StringUtils.trimToNull(item.getGenus()));
    }

    return builder.build();
  }

  private Classification createEmptyClassification() {
    Classification classification = new Classification();
    classification.setSuccess(false);
    classification.setMatchType("noMatch");
    return classification;
  }

  public long getDistinctTaxaCount(String speciesListID) {
    logger.info("Getting distinct taxonConceptID count for speciesListID: " + speciesListID);
    try {
      Long count =
          speciesListItemRepository.countDistinctTaxonConceptIDBySpeciesListID(speciesListID);
      return count != null ? count : 0L;
    } catch (Exception e) {
      logger.error(
          "Error fetching distinct taxonConceptID count for speciesListID: " + speciesListID, e);
    }

    return 0L;
  }

  private void logMemoryUsage(String context) {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory() / 1024 / 1024;
    long totalMemory = runtime.totalMemory() / 1024 / 1024;
    long freeMemory = runtime.freeMemory() / 1024 / 1024;
    long usedMemory = totalMemory - freeMemory;

    logger.info(
        "[Memory|{}] Used: {}MB, Free: {}MB, Total: {}MB, Max: {}MB",
        context,
        usedMemory,
        freeMemory,
        totalMemory,
        maxMemory);

    double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
    if (memoryUsagePercent > 80) {
      logger.warn(
          "[Memory|{}] High memory usage: {:.1f}% - consider running GC or reducing batch sizes",
          context, memoryUsagePercent);
    }
  }
}
