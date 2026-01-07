/*
 * Copyright (C) 2025 Atlas of Living Australia
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    
    @Value("${namematching.serviceURL:https://namematching-ws.ala.org.au}")
    private String nameMatchingServiceUrl;
    
    @Value("${namematching.threadPoolSize:10}")
    private int threadPoolSize;
    
    @Value("${namematching.maxConcurrentRequests:20}")
    private int maxConcurrentRequests;
    
    @Value("${namematching.dataCacheConfig.entryCapacity:20000}")
    private int cacheEntryCapacity;
    
    @Value("${namematching.dataCacheConfig.enableJmx:false}")
    private boolean cacheEnableJmx;
    
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
            // Set eternal=true to avoid needing expiry configuration
            DataCacheConfiguration dataCacheConfig = DataCacheConfiguration.builder()
                    .entryCapacity(cacheEntryCapacity)
                    .enableJmx(cacheEnableJmx)
                    .eternal(true)  // Always use eternal=true to avoid expiry complications
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

        allLists.forEach(speciesList -> {
            try {
                progressService.updateMigrationProgress(speciesList);

                long distinctMatchCount = taxonMatchDataset(speciesList.getId());
                speciesList.setDistinctMatchCount(distinctMatchCount);
                speciesListMongoRepository.save(speciesList);
                reindex(speciesList.getId());
            } catch (Exception e) {
                logger.error("taxonMatchDatasets() error: {}", e.getMessage(), e);
            }
        });

        progressService.clearMigrationProgress();
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

        int batchSize = 50;
        ObjectId lastId = null;

        Set<String> distinctTaxa = new HashSet<>();

        boolean finished = false;
        while (!finished) {
            long startTime = System.nanoTime();

            List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(speciesList.getId(), lastId,
                    PageRequest.of(0, batchSize));
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
     * This method performs parallel lookups with rate limiting to avoid overwhelming the service.
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

            // Create a list of CompletableFutures for parallel processing
            List<CompletableFuture<NameUsageMatch>> futures = new ArrayList<>();
            
            for (int i = 0; i < nameSearches.size(); i++) {
                final int index = i;
                final NameSearch search = nameSearches.get(i);
                
                CompletableFuture<NameUsageMatch> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Acquire permit from rate limiter
                        rateLimiter.acquire();
                        try {
                            NameUsageMatch result = nameMatchService.match(search);
                            return result != null ? result : createEmptyNameUsageMatch();
                        } finally {
                            // Release permit
                            rateLimiter.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("[{}|taxonMatch] Thread interrupted for item {}", speciesListID, index, e);
                        return createEmptyNameUsageMatch();
                    } catch (Exception e) {
                        logger.error("[{}|taxonMatch] Error matching item {}: {}", speciesListID, index, e.getMessage());
                        return createEmptyNameUsageMatch();
                    }
                }, executorService);
                
                futures.add(future);
            }

            // Wait for all futures to complete and collect results
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            
            // Wait with timeout
            allFutures.get(5, TimeUnit.MINUTES);
            
            // Collect results in order
            List<NameUsageMatch> matches = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            long elapsed = (System.nanoTime() - startTime) / 1_000_000;
            logger.info("[{}|taxonMatch] Completed {} parallel lookups in {}ms", 
                    speciesListID, matches.size(), elapsed);

            // Convert NameUsageMatch to Classification
            return convertToClassifications(matches);

        } catch (Exception e) {
            logger.error("[{}|taxonMatch] Exception during parallel lookup: {}", speciesListID, e.getMessage(), e);
            // Return empty classifications for all items
            return items.stream()
                    .map(item -> createEmptyClassification())
                    .collect(Collectors.toList());
        }
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
     * Count actual items in MongoDB and update the SpeciesList document's rowCount field.
     * To fix issue where rowCount is out of sync with actual data (or null). 
     * 
     * @param speciesListID
     */ 
    public void repairRowCount(String speciesListID) {  
        int batchSize = 10000;  
        ObjectId lastId = null;  
        long actualCount = 0;  
        
        boolean finished = false;  
        while (!finished) {  
            List<SpeciesListItem> items = speciesListItemMongoRepository  
                    .findNextBatch(speciesListID, lastId, PageRequest.of(0, batchSize));  
            
            if (items.isEmpty()) {  
                finished = true;  
            } else {  
                actualCount += items.size();  
                lastId = items.get(items.size() - 1).getId();  
            }  
        }  
        
        // Update the SpeciesList document  
        Optional<SpeciesList> list = speciesListMongoRepository.findById(speciesListID);  
        if (list.isPresent()) {  
            list.get().setRowCount((int) actualCount);  
            speciesListMongoRepository.save(list.get());  
        }  
    }
}