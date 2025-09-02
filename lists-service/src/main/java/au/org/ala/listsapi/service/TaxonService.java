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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;

@Service
public class TaxonService {

    @Value("${namematching.url}")
    private String nameMatchingQueryUrl;

    private static final Logger logger = LoggerFactory.getLogger(TaxonService.class);
    public static final String SPECIES_LIST_ID = "speciesListID";
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

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                                logger.error(e.getMessage(), e);
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
        int size = 10;
        int page = 0;
        boolean done = false;

        progressService.setupMigrationProgress(speciesListMongoRepository.count());

        while (!done) {
            Pageable paging = PageRequest.of(page, size);
            Page<SpeciesList> speciesLists = speciesListMongoRepository.findAll(paging);
            if (!speciesLists.getContent().isEmpty()) {
                speciesLists.forEach(
                        speciesList -> {
                            try {
                                progressService.updateMigrationProgress(speciesList);

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

        int batchSize = 200;
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

                    // Update classifications
                    updateClassifications(items);

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
                // Update "matchType" based on classification success
                if (classification.get(i).getSuccess() == false) {
                    classification.get(i).setMatchType("noMatch");
                }

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

    /**
     * Classification lookup using a list of SpeciesListItem objects via the name
     * matching API.
     * This method performs two lookups:
     * 1. Using the searchAllByClassification endpoint to get classifications based
     * on taxon maps (scientificName).
     * 2. Using the getAllByTaxonID endpoint to get classifications based on taxon
     * IDs (a Spatial Portal requirement)
     * The results from both lookups are (destructively) merged and returned.
     * This allows for a mixture of scientific names and taxon IDs to be provided in
     * the
     * input list via the scientificName column - so it is backwards compatible with
     * old lists app.
     * 
     * @param items List of SpeciesListItem objects to look up.
     * @return List of Classification objects.
     * @throws IOException          If an I/O error occurs during the lookup.
     * @throws InterruptedException If the thread is interrupted during the lookup.
     */
    public List<Classification> lookupTaxa(List<SpeciesListItem> items) throws IOException, InterruptedException { // Declared
                                                                                                                   // specific
                                                                                                                   // exceptions
        if (items == null || items.isEmpty()) {
            return new ArrayList<>(); // Return empty list for no items
        }

        String speciesListID = items.get(0).getSpeciesListID(); // Assuming consistent ID across items

        // Perform lookup using searchAllByClassification
        List<Classification> classifications1 = performClassificationLookup(
                items.stream().map(SpeciesListItem::toTaxonMap).collect(Collectors.toList()),
                "/api/searchAllByClassification",
                speciesListID,
                "taxonMatch");

        // Perform lookup using getAllByTaxonID
        List<Classification> classifications2 = performTaxonIdLookup(
                items.stream().flatMap(item -> item.toTaxonList().stream()).collect(Collectors.toList()),
                "/api/getAllByTaxonID?follow=true&",
                speciesListID,
                "taxonIDLookup");

        return mergeClassifications(classifications1, classifications2);
    }

    private <T> List<Classification> performClassificationLookup(
            T requestBody, String endpoint, String speciesListID, String logTag)
            throws IOException, InterruptedException {

        String json = objectMapper.writeValueAsString(requestBody);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(nameMatchingQueryUrl + endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();

        return sendRequestAndParseResponse(httpRequest, speciesListID, logTag);
    }

    private List<Classification> performTaxonIdLookup(
            List<String> taxonIDs, String endpoint, String speciesListID, String logTag)
            throws IOException, InterruptedException {

        // Ensure the encodedParams contains the same number of entries as taxonIDs,
        // inserting empty values for nulls (nulls trigger an exception when encoded)
        String encodedParams = taxonIDs.stream()
            .map(id -> "taxonIDs=" + (id == null ? "" : URLEncoder.encode(id, StandardCharsets.UTF_8)))
            .collect(Collectors.joining("&"));

        URI uriWithParams = URI.create(nameMatchingQueryUrl + endpoint + encodedParams);

        HttpRequest httpRequest = HttpRequest.newBuilder(uriWithParams)
                .POST(HttpRequest.BodyPublishers.noBody()) // No body for GET
                .header("Content-Type", "application/json")
                .build();

        return sendRequestAndParseResponse(httpRequest, speciesListID, logTag);
    }

    private List<Classification> sendRequestAndParseResponse(
            HttpRequest httpRequest, String speciesListID, String logTag)
            throws IOException, InterruptedException {

        long requestStart = System.nanoTime();
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        long requestElapsed = (System.nanoTime() - requestStart) / 1_000_000;
        logger.info("[{}|{}] Request took {}ms", speciesListID, logTag, requestElapsed);

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), new TypeReference<List<Classification>>() {
            });
        } else {
            logger.error("Classification lookup failed for {}: Status code {}", logTag, response.statusCode());
            return new ArrayList<>();
        }
    }

    /**
     * Merges two lists of Classification objects, producing a list of the same size
     * as the input lists.
     * It prioritizing the first list's values if both lists have values at the same
     * index.
     *
     * @param classifications1 First list of Classification objects.
     * @param classifications2 Second list of Classification objects.
     * @return Merged list of Classification objects.
     */
    private List<Classification> mergeClassifications(
            List<Classification> classifications1, List<Classification> classifications2) {
        List<Classification> mergedClassifications = new ArrayList<>();
        int maxSize = Math.max(classifications1.size(), classifications2.size());

        for (int i = 0; i < maxSize; i++) {
            Classification c1 = (i < classifications1.size()) ? classifications1.get(i) : null;
            Classification c2 = (i < classifications2.size()) ? classifications2.get(i) : null;

            if (c1 != null && c1.getTaxonConceptID() != null) {
                mergedClassifications.add(c1);
            } else if (c2 != null && c2.getTaxonConceptID() != null) {
                mergedClassifications.add(c2);
            } else if (c1 != null) { // Fallback to c1 if c2 is null or doesn't have ID
                mergedClassifications.add(c1);
            } else if (c2 != null) { // Fallback to c2 if c1 is null
                mergedClassifications.add(c2);
            } else {
                // Both are null for this index, potentially add a null or skip based on desired
                // behavior
                // For now, we'll skip if both are null, assuming the lists are aligned by input
            }
        }
        return mergedClassifications;
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

        return 0L; // Return 0 if an error occurs
    }

}
