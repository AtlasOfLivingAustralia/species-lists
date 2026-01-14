package au.org.ala.listsapi.service;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.names.ws.api.NameMatchService;
import au.org.ala.names.ws.api.NameSearch;
import au.org.ala.names.ws.api.NameUsageMatch;
import au.org.ala.names.ws.client.ALANameUsageMatchServiceClient;
import au.org.ala.ws.ClientConfiguration;
import au.org.ala.ws.DataCacheConfiguration;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class TaxonService {

    private static final Logger logger = LoggerFactory.getLogger(TaxonService.class);
    public static final String SPECIES_LIST_ID = "speciesListID";
    
    @Value("${namematching.url:https://namematching-ws.ala.org.au}")
    private String nameMatchingServiceUrl;
    
    @Value("${namematching.bulkMatchBatchSize:200}")
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

    @Autowired
    protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
    
    @Autowired
    protected SpeciesListMongoRepository speciesListMongoRepository;
    
    @Autowired
    protected ElasticsearchOperations elasticsearchOperations;
    
    @Autowired
    protected ProgressService progressService;
    
    @Autowired
    protected SearchHelperService searchHelperService;

    private NameMatchService nameMatchService;
    private ExecutorService executorService;
    private Semaphore rateLimiter;

    /**
     * Initialize the name matching client and thread pool after properties are set
     */
    @PostConstruct
    public void init() {
        try {
            // Initialize the ALANameUsageMatchServiceClient with caching
            DataCacheConfiguration dataCacheConfig = DataCacheConfiguration.builder()
                    .entryCapacity(cacheEntryCapacity)
                    .enableJmx(cacheEnableJmx)
                    .eternal(cacheEternal)
                    .keepDataAfterExpired(cacheKeepDataAfterExpired)
                    .permitNullValues(cachePermitNullValues)
                    .suppressExceptions(cacheSuppressExceptions)
                    .build();

            ClientConfiguration clientConfig = ClientConfiguration.builder()
                    .baseUrl(new java.net.URL(nameMatchingServiceUrl))
                    .dataCache(dataCacheConfig)
                    .build();
            
            nameMatchService = new ALANameUsageMatchServiceClient(clientConfig);
            
            // Initialize thread pool
            executorService = Executors.newFixedThreadPool(threadPoolSize);
            
            // Initialize rate limiter (semaphore to control concurrent requests)
            rateLimiter = new Semaphore(maxConcurrentRequests);
            
            logger.info("TaxonService initialized with threadPoolSize={}, maxConcurrentRequests={}", 
                    threadPoolSize, maxConcurrentRequests);
        } catch (Exception e) {
            logger.error("Failed to initialize TaxonService", e);
            throw new RuntimeException("Failed to initialize TaxonService", e);
        }
    }

    /**
     * Cleanup thread pool on service destruction
     */
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
        logger.info("Indexing all datasets");
        int size = 1000;
        int page = 0;
        boolean done = false;
        AtomicInteger datasetsIndex = new AtomicInteger();

        progressService.setupMigrationProgress(speciesListMongoRepository.count());

        while (!done) {
            Pageable paging = PageRequest.of(page, size);
            Page<SpeciesList> speciesLists = speciesListMongoRepository.findAll(paging);
            if (speciesLists.getContent().size() > 0) {
                speciesLists.forEach(
                        speciesList -> {
                            try {
                                progressService.updateMigrationProgress(speciesList);
                                reindex(speciesList.getId());
                                datasetsIndex.getAndIncrement();
                            } catch (Exception e) {
                                logger.error("reindex() exception: {}", e.getMessage(), e);
                            }
                        });
            } else {
                done = true;
            }
            page++;
        }

        progressService.clearMigrationProgress();
        logger.info("Indexing of all datasets complete. " + datasetsIndex + " datasets indexed.");
    }

    @Async("processExecutor")
    public void taxonMatchDatasets() {
        long overallStartTime = System.nanoTime();
        logger.info("Taxon matching all datasets");
        
        progressService.setupMigrationProgress(speciesListMongoRepository.count());

        // Fetch all species lists and sort by rowCount descending
        // This maximizes cache hits by processing largest lists first
        List<SpeciesList> allLists = new ArrayList<>();
        int page = 0;
        int size = 1000;
        boolean done = false;
        
        logger.info("Fetching all species lists for sorting...");
        while (!done) {
            Pageable paging = PageRequest.of(page, size);
            Page<SpeciesList> speciesLists = speciesListMongoRepository.findAll(paging);
            if (!speciesLists.getContent().isEmpty()) {
                allLists.addAll(speciesLists.getContent());
            } else {
                done = true;
            }
            page++;
        }
        
        // Sort by rowCount descending (nulls last)
        allLists.sort((a, b) -> {
            Integer countA = a.getRowCount();
            Integer countB = b.getRowCount();
            if (countA == null && countB == null) return 0;
            if (countA == null) return 1;  // nulls last
            if (countB == null) return -1; // nulls last
            return countB.compareTo(countA); // descending
        });
        
        logger.info("Processing {} species lists sorted by size (largest first)", allLists.size());

        AtomicInteger processedCount = new AtomicInteger(0);
        
        // Process lists in parallel with controlled concurrency
        // Use ForkJoinPool to limit parallelism
        ForkJoinPool customThreadPool = new ForkJoinPool(datasetProcessingParallelism);
        try {
            customThreadPool.submit(() ->
                allLists.parallelStream().forEach(speciesList -> {
                    try {
                        progressService.updateMigrationProgress(speciesList);

                        long distinctMatchCount = taxonMatchDataset(speciesList.getId());
                        speciesList.setDistinctMatchCount(distinctMatchCount);
                        speciesListMongoRepository.save(speciesList);
                        
                        int processed = processedCount.incrementAndGet();
                        logger.info("Completed {}/{} lists. List {} had {} distinct taxa.", 
                                processed, allLists.size(), speciesList.getId(), distinctMatchCount);
                        
                    } catch (Exception e) {
                        logger.error("taxonMatchDatasets() error: {}", e.getMessage(), e);
                    }
                })
            ).get(); // Wait for completion
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
        
        logger.info("Taxon matching all {} datasets complete. Total time: {}m {}s ({} ms)", 
                allLists.size(), minutes, seconds, overallElapsed);
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
                Optional<IndexQuery> failedItem = updateList.stream().filter(item -> failedIds.contains(item.getId()))
                        .findFirst();
                ObjectMapper mapper = new ObjectMapper();

                logger.error(" -- FAILED DOCUMENT EXAMPLE --");
                logger.error(mapper.writeValueAsString(failedItem.get()));
                logger.error(" -- FAILED DOCUMENTS --");
            } catch (JsonProcessingException ex) {
                logger.error("Failed to write update list to console", ex);
            }
        }
        long elapsed = System.nanoTime() - startTime;
        logger.info(
                "[{}|reindex|bulkIndex] Indexing " + updateList.size() + " items took " + (elapsed / 1000000) + "ms",
                list.getId());
    }

    private SpeciesListIndex listItemToIndex(SpeciesList speciesList, SpeciesListItem speciesListItem) {
        // write the data to Elasticsearch
        return new SpeciesListIndex(
                speciesListItem.getId().toString(),
                speciesList.getDataResourceUid(),
                speciesList.getTitle(),
                speciesList.getListType(),
                speciesListItem.getSpeciesListID(),
                speciesList.getDescription(),
                speciesList.getLicence(),
                speciesListItem.getSuppliedName(),
                speciesListItem.getScientificName(),
                speciesListItem.getVernacularName(),
                speciesListItem.getTaxonID(),
                speciesListItem.getKingdom(),
                speciesListItem.getPhylum(),
                speciesListItem.getClasss(),
                speciesListItem.getOrder(),
                speciesListItem.getFamily(),
                speciesListItem.getGenus(),
                speciesListItem.getProperties(),
                speciesListItem.getClassification(),
                speciesList.getIsPrivate() != null ? speciesList.getIsPrivate() : false,
                speciesList.getIsAuthoritative() != null ? speciesList.getIsAuthoritative() : false,
                speciesList.getIsBIE() != null ? speciesList.getIsBIE() : false,
                speciesList.getIsSDS() != null ? speciesList.getIsSDS() : false,
                speciesList.getIsThreatened() != null ? speciesList.getIsThreatened() : false,
                speciesList.getIsInvasive() != null ? speciesList.getIsInvasive() : false,
                StringUtils.isNotEmpty(speciesList.getRegion()) || StringUtils.isNotEmpty(speciesList.getWkt()),
                speciesList.getOwner(),
                speciesList.getEditors(),
                speciesList.getTags() != null ? speciesList.getTags() : new ArrayList<>(),
                speciesList.getDateCreated() != null ? speciesList.getDateCreated().toString() : null,
                speciesList.getLastUpdated() != null ? speciesList.getLastUpdated().toString() : null,
                speciesList.getLastUpdatedBy());
    }

    public void reindex(String speciesListID) {
        logger.info("[{}|reindex] Starting indexing", speciesListID);
        long findByIdStart = System.nanoTime();
        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID,
                speciesListID);
        long findByIdElapsed = (System.nanoTime() - findByIdStart) / 1000000;
        logger.info("[{}|reindex] Reindex find by ID {}ms", speciesListID, findByIdElapsed);

        if (optionalSpeciesList.isEmpty())
            return;

        SpeciesList speciesList = optionalSpeciesList.get();

        int batchSize = 1000;
        ObjectId lastId = null;

        boolean finished = false;
        while (!finished) {
            long startTime = System.nanoTime();

            List<SpeciesListItem> speciesListItems = speciesListItemMongoRepository.findNextBatch(speciesList.getId(),
                    lastId, PageRequest.of(0, batchSize));

            long elapsed = System.nanoTime() - startTime;

            logger.info("[{}|reindex] Fetched {} items in {} ms",
                    speciesListID, speciesListItems.size(), elapsed / 1_000_000);

            if (!speciesListItems.isEmpty()) {
                List<IndexQuery> updateList = new ArrayList<>();
                for (SpeciesListItem item : speciesListItems) {
                    SpeciesListIndex indexItem = listItemToIndex(speciesList, item);
                    updateList.add(
                            new IndexQueryBuilder()
                                    .withId(item.getId().toString())
                                    .withObject(indexItem)
                                    .build());
                }

                try {
                    bulkIndexSafe(updateList, speciesList);
                    updateList.clear();
                } catch (Exception e) {
                    logger.error("reindex({}) exception: {}", speciesListID, e.getMessage(), e);
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
        logMemoryUsage("Start of taxonMatchDataset");

        long findByIdStart = System.nanoTime();
        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID,
                speciesListID);
        long findByIdElapsed = (System.nanoTime() - findByIdStart) / 1000000;
        logger.info("[{}|taxonMatch] Taxon match find by ID OR UID {}ms", speciesListID, findByIdElapsed);

        if (optionalSpeciesList.isEmpty())
            return 0;
        SpeciesList speciesList = optionalSpeciesList.get();

        // Reset ingestion progress
        long resetProgressStart = System.nanoTime();
        progressService.resetIngestProgress(speciesList.getId());
        long resetProgressElapsed = (System.nanoTime() - resetProgressStart) / 1000000;
        logger.info("[{}|taxonMatch] Reset ingest progress {}ms", speciesListID, resetProgressElapsed);
        logger.info("[{}|taxonMatch] Started taxon matching", speciesListID);

        ObjectId lastId = null;

        Set<String> distinctTaxa = new HashSet<>();

        boolean finished = false;
        while (!finished) {
            long startTime = System.nanoTime();

            List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(speciesList.getId(), lastId,
                    PageRequest.of(0, bulkMatchBatchSize));
            long elapsed = System.nanoTime() - startTime;

            logger.info("[{}|taxonMatch] Fetched {} items in {} ms",
                    speciesListID, items.size(), elapsed / 1_000_000);

            if (items.isEmpty()) {
                finished = true;
            } else {
                try {
                    // Update classifications using the new multi-threaded approach
                    updateClassifications(items, speciesList);

                    // Save updated items
                    long saveClassStart = System.nanoTime();
                    searchHelperService.speciesListItemsBulkUpdate(items, List.of("classification"));
                    long saveClassElapsed = (System.nanoTime() - saveClassStart) / 1000000;
                    logger.info("[{}|taxonMatch] Save updated classification took {}ms", speciesListID,
                            saveClassElapsed);

                    // Record progress
                    long updatedProgressStart = System.nanoTime();
                    progressService.addIngestMongoProgress(speciesList.getId(), items.size());
                    long updateProgressElapsed = (System.nanoTime() - updatedProgressStart) / 1000000;
                    logger.info("[{}|taxonMatch] Update mongo progress {}ms", speciesListID, updateProgressElapsed);

                    items.forEach(speciesListItem -> {
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

        logger.info("[{}|taxonMatch] Taxon matching complete. Found {} distinct taxa.",
                speciesListID, distinctTaxa.size());

        return distinctTaxa.size();
    }

    /**
     * Update classifications for a list of species list items using multi-threaded lookups
     * 
     * @param speciesListItems List of items to update
     * @param speciesList The parent species list
     */
    public void updateClassifications(List<SpeciesListItem> speciesListItems, SpeciesList speciesList) {
        try {
            List<Classification> classifications = lookupTaxa(speciesListItems, speciesList);
            for (int i = 0; i < speciesListItems.size(); i++) {
                Classification classification = classifications.get(i);
                
                // Update "matchType" based on classification success
                if (classification != null && !classification.getSuccess()) {
                    classification.setMatchType("noMatch");
                }
                
                speciesListItems.get(i).setClassification(classification);
            }
        } catch (Exception e) {
            logger.error("updateClassifications() exception: {}", e.getMessage(), e);
        }
    }

    /**
     * Lookup a single taxon (convenience method with automatic species list lookup)
     * 
     * @param item The species list item
     * @return Classification result
     */
    public Classification lookupTaxon(SpeciesListItem item) {
        try {
            // Fetch the species list
            Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findById(item.getSpeciesListID());
            SpeciesList speciesList = optionalSpeciesList.orElse(null);
            
            return lookupTaxon(item, speciesList);
        } catch (Exception e) {
            logger.error("lookupTaxon() exception: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Lookup a single taxon (convenience method)
     * 
     * @param item The species list item
     * @param speciesList The parent species list
     * @return Classification result
     */
    public Classification lookupTaxon(SpeciesListItem item, SpeciesList speciesList) {
        try {
            List<Classification> results = lookupTaxa(List.of(item), speciesList);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            logger.error("lookupTaxon() exception: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Multi-threaded classification lookup using ALANameUsageMatchServiceClient.
     * This method uses the bulk matchAll() API and processes multiple batches in parallel
     * with rate limiting to avoid overwhelming the service.
     * 
     * @param items List of SpeciesListItem objects to look up
     * @param speciesList The parent species list
     * @return List of Classification objects in the same order as input
     */
    public List<Classification> lookupTaxa(List<SpeciesListItem> items, SpeciesList speciesList) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        String speciesListID = items.get(0).getSpeciesListID();
        long startTime = System.nanoTime();
        
        try {
            // Build NameSearch objects for all items
            List<NameSearch> nameSearches = items.stream()
                    .map(item -> buildNameSearch(item, speciesList))
                    .collect(Collectors.toList());

            logger.info("[{}|taxonMatch] Built {} name searches", speciesListID, nameSearches.size());

            // If the batch is small enough, just use matchAll() directly without threading
            if (nameSearches.size() <= bulkMatchBatchSize) {
                return performBulkMatch(nameSearches, speciesListID, startTime);
            }

            // Split into batches for parallel processing
            List<List<NameSearch>> batches = partitionList(nameSearches, bulkMatchBatchSize);
            logger.info("[{}|taxonMatch] Split into {} batches of up to {} items", 
                    speciesListID, batches.size(), bulkMatchBatchSize);

            // Create CompletableFutures for each batch
            List<CompletableFuture<List<NameUsageMatch>>> futures = new ArrayList<>();
            
            for (int i = 0; i < batches.size(); i++) {
                final int batchIndex = i;
                final List<NameSearch> batch = batches.get(i);
                
                CompletableFuture<List<NameUsageMatch>> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Acquire permit from rate limiter
                        long semaphoreWaitStart = System.nanoTime();
                        rateLimiter.acquire();
                        long semaphoreWaitTime = (System.nanoTime() - semaphoreWaitStart) / 1_000_000;
                        
                        if (semaphoreWaitTime > 100) {
                            logger.warn("[{}|taxonMatch|batch-{}] Semaphore wait time: {}ms (bottleneck?)", 
                                    speciesListID, batchIndex, semaphoreWaitTime);
                        }
                        
                        try {
                            long batchStart = System.nanoTime();
                            List<NameUsageMatch> results = nameMatchService.matchAll(batch);
                            long batchElapsed = (System.nanoTime() - batchStart) / 1_000_000;
                            
                            logger.info("[{}|taxonMatch|batch-{}] Matched {} items in {}ms", 
                                    speciesListID, batchIndex, batch.size(), batchElapsed);
                            
                            // Ensure we return the same number of results as inputs
                            if (results == null || results.size() != batch.size()) {
                                logger.warn("[{}|taxonMatch|batch-{}] Result size mismatch. Expected {}, got {}", 
                                        speciesListID, batchIndex, batch.size(), 
                                        results == null ? 0 : results.size());
                                return fillMissingMatches(results, batch.size());
                            }
                            
                            return results;
                        } finally {
                            // Release permit
                            rateLimiter.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("[{}|taxonMatch|batch-{}] Thread interrupted", speciesListID, batchIndex, e);
                        return createEmptyMatches(batch.size());
                    } catch (Exception e) {
                        logger.error("[{}|taxonMatch|batch-{}] Error matching batch: {}", 
                                speciesListID, batchIndex, e.getMessage(), e);
                        return createEmptyMatches(batch.size());
                    }
                }, executorService);
                
                futures.add(future);
            }

            // Wait for all futures to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            
            // Wait with timeout
            allFutures.get(5, TimeUnit.MINUTES);
            
            // Collect and flatten results in order
            List<NameUsageMatch> allMatches = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            long elapsed = (System.nanoTime() - startTime) / 1_000_000;
            logger.info("[{}|taxonMatch] Completed {} bulk lookups in {}ms ({} batches)", 
                    speciesListID, allMatches.size(), elapsed, batches.size());

            // Convert NameUsageMatch to Classification
            return convertToClassifications(allMatches);

        } catch (Exception e) {
            logger.error("[{}|taxonMatch] Exception during bulk lookup: {}", speciesListID, e.getMessage(), e);
            // Return empty classifications for all items
            return items.stream()
                    .map(item -> createEmptyClassification())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Perform a single bulk match operation (non-threaded helper)
     */
    private List<Classification> performBulkMatch(List<NameSearch> nameSearches, String speciesListID, long startTime) {
        try {
            rateLimiter.acquire();
            try {
                List<NameUsageMatch> matches = nameMatchService.matchAll(nameSearches);
                
                long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("[{}|taxonMatch] Completed {} bulk lookups in {}ms (single batch)", 
                        speciesListID, matches.size(), elapsed);
                
                // Ensure we have the right number of results
                if (matches == null || matches.size() != nameSearches.size()) {
                    logger.warn("[{}|taxonMatch] Result size mismatch. Expected {}, got {}", 
                            speciesListID, nameSearches.size(), 
                            matches == null ? 0 : matches.size());
                    matches = fillMissingMatches(matches, nameSearches.size());
                }
                
                return convertToClassifications(matches);
            } finally {
                rateLimiter.release();
            }
        } catch (Exception e) {
            logger.error("[{}|taxonMatch] Exception during bulk lookup: {}", speciesListID, e.getMessage(), e);
            return nameSearches.stream()
                    .map(ns -> createEmptyClassification())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Partition a list into smaller batches
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    /**
     * Create a list of empty matches for error cases
     */
    private List<NameUsageMatch> createEmptyMatches(int count) {
        List<NameUsageMatch> matches = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            matches.add(null);
        }
        return matches;
    }

    /**
     * Fill in missing matches if the API returns fewer results than expected
     */
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

    /**
     * Convert NameUsageMatch objects to Classification objects
     * 
     * @param matches List of NameUsageMatch objects
     * @return List of Classification objects
     */
    private List<Classification> convertToClassifications(List<NameUsageMatch> matches) {
        return matches.stream()
                .map(this::convertToClassification)
                .collect(Collectors.toList());
    }

    /**
     * Convert a single NameUsageMatch to Classification
     * 
     * @param match The NameUsageMatch object
     * @return Classification object
     */
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
        classification.setMatchType(match.getMatchType() != null ? match.getMatchType().toString() : null);
        
        return classification;
    }

    /**
     * Build a NameSearch object from a SpeciesListItem
     * This method replicates the logic from ColumnMatchingService.buildSearch()
     * 
     * @param item The species list item
     * @param speciesList The parent species list
     * @return NameSearch object
     */
    private NameSearch buildNameSearch(SpeciesListItem item, SpeciesList speciesList) {
        NameSearch.NameSearchBuilder builder = NameSearch.builder();
        
        // Set scientific name (primary identifier)
        if (StringUtils.isNotBlank(item.getScientificName())) {
            builder.scientificName(StringUtils.trimToNull(item.getScientificName()));
        }
        // Set taxonID if available
        if (StringUtils.isNotBlank(item.getTaxonID())) {
            builder.taxonID(StringUtils.trimToNull(item.getTaxonID()));
        }
        // Set vernacular/common name
        if (StringUtils.isNotBlank(item.getVernacularName())) {
            builder.vernacularName(StringUtils.trimToNull(item.getVernacularName()));
        }
        // Set taxonomic hierarchy
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

    /**
     * Create an empty NameUsageMatch for cases where the API returns null
     * Since NameUsageMatch constructor is not public, we return null and handle it in conversion
     * 
     * @return null (will be handled by convertToClassification)
     */
    private NameUsageMatch createEmptyNameUsageMatch() {
        return null;
    }

    /**
     * Create an empty Classification object
     * 
     * @return Empty Classification
     */
    private Classification createEmptyClassification() {
        Classification classification = new Classification();
        classification.setSuccess(false);
        classification.setMatchType("noMatch");
        return classification;
    }

    public long getDistinctTaxaCount(String speciesListID) {
        logger.info("Getting distinct taxonConceptID count for speciesListID: " + speciesListID);
        try {
            // Use NativeQueryBuilder to construct the query
            NativeQueryBuilder queryBuilder = NativeQuery.builder();

            // Add a filter to match the provided speciesListID
            queryBuilder.withQuery(q -> q
                    .bool(b -> b.filter(f -> f.term(t -> t.field(SPECIES_LIST_ID + ".keyword").value(speciesListID)))));

            // Add a cardinality aggregation for distinct taxonConceptID
            queryBuilder.withAggregation(
                    "distinctTaxonConceptID",
                    Aggregation.of(a -> a.cardinality(c -> c.field("classification.taxonConceptID.keyword"))));

            // Build the query
            NativeQuery nativeQuery = queryBuilder.build();

            // Execute the query using ElasticsearchOperations
            SearchHits<SpeciesListIndex> searchHits = elasticsearchOperations.search(nativeQuery,
                    SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

            // Extract the aggregation result
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
            ElasticsearchAggregation cardinalityAgg = aggregations.aggregations().stream()
                    .filter(agg -> agg.aggregation().getName().equals("distinctTaxonConceptID"))
                    .findFirst()
                    .orElse(null);

            if (cardinalityAgg != null) {
                return cardinalityAgg.aggregation().getAggregate().cardinality().value();
            }

        } catch (Exception e) {
            logger.error("Error fetching distinct taxonConceptID count for speciesListID: " + speciesListID, e);
        }

        return 0L;
    }

    /**
     * Log current memory usage for debugging
     */
    private void logMemoryUsage(String context) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        logger.info("[Memory|{}] Used: {}MB, Free: {}MB, Total: {}MB, Max: {}MB", 
                context, usedMemory, freeMemory, totalMemory, maxMemory);
        
        // Warn if memory usage is high
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        if (memoryUsagePercent > 80) {
            logger.warn("[Memory|{}] High memory usage: {:.1f}% - consider running GC or reducing batch sizes", 
                    context, memoryUsagePercent);
        }
    }
}