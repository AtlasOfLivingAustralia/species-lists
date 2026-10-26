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

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;

@Service
public class BiocacheService {
    @Autowired
    protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @Autowired
    protected SearchHelperService searchHelperService;

    public static final Logger log = LoggerFactory.getLogger(BiocacheService.class);

    @Value("${biocache.api.url}")
    private String biocacheUrl;

    private final int maxBooleanClause = 1000;

    // Page size used when paging through list items, and maximum number of taxa
    // included in a QID (10 batches of 200 = 2000)
    private static final int PAGE_SIZE = 200;
    private static final int MAX_TAXA = 10 * PAGE_SIZE;

    // Helper method to batch lists
    private <T> Stream<List<T>> batches(List<T> source) {
        int size = source.size();
        if (size <= 0)
            return Stream.empty();
        int fullChunks = (size - 1) / maxBooleanClause;
        return IntStream.range(0, fullChunks + 1).mapToObj(
                n -> source.subList(n * maxBooleanClause, n == fullChunks ? size : (n + 1) * maxBooleanClause));
    }

    private String listBatchToString(List<SpeciesListItem> listBatch) {
        return "(" + listBatch.stream().map((speciesListItem -> {
            Classification classification = speciesListItem.getClassification();

            if (classification != null && classification.getTaxonConceptID() != null) {
                return "lsid:" + classification.getTaxonConceptID();
            } else if (speciesListItem.getScientificName() != null) {
                return "raw_scientificName:\"" + speciesListItem.getScientificName() + "\"";
            }

            return null;
        })).filter(Objects::nonNull).collect(Collectors.joining(" OR ")) + ")";
    }

    public String getQidForSpeciesList(String speciesListId) throws Exception {
        return getQidForItems(collectUnfilteredItems(speciesListId));
    }

    /**
     * Generates a QID for a species list restricted to the items matching the given
     * filters. Uses the same Elasticsearch query as the single list page
     * (see SearchHelperService#searchSingleSpeciesList), so the resulting taxa set
     * matches exactly what a user sees when the filters are applied in the UI.
     */
    public String getQidForSpeciesList(SpeciesList speciesList, List<Filter> filters) throws Exception {
        return getQidForItems(collectFilteredItems(speciesList, filters));
    }

    List<SpeciesListItem> collectUnfilteredItems(String speciesListId) {
        List<SpeciesListItem> items = new ArrayList<>();
        List<SpeciesListItem> batch;

        int page = 0;
        int batchCount = 0;
        do {
            if (batchCount * PAGE_SIZE >= MAX_TAXA) {
                log.warn("Reached the maximum number of taxa: {}", MAX_TAXA);
                break;
            }

            batch = speciesListItemMongoRepository
                .findNextBatch(speciesListId, null, PageRequest.of(page, PAGE_SIZE));
            if (batch != null && !batch.isEmpty()) {
                items.addAll(batch);
            }

            page++;
            batchCount++;
        } while (batch != null && batch.size() == PAGE_SIZE);

        return items;
    }

    List<SpeciesListItem> collectFilteredItems(SpeciesList speciesList, List<Filter> filters) {
        // Mirror the defaults used by the single list page
        // (see GraphQLController#buildSingleListSearchContext)
        SingleListSearchContext context = SingleListSearchContext.builder()
            .speciesListId(speciesList.getId())
            .speciesList(speciesList)
            .searchQuery("")
            .filters(filters)
            .sort("scientificName")
            .dir("asc")
            .build();

        List<SpeciesListItem> items = new ArrayList<>();
        int page = 0;
        while (items.size() < MAX_TAXA) {
            Page<SpeciesListItem> result =
                searchHelperService.searchSingleSpeciesList(context, PageRequest.of(page, PAGE_SIZE));
            items.addAll(result.getContent());
            if (result.getContent().size() < PAGE_SIZE)
                break;
            page++;
        }

        if (items.size() >= MAX_TAXA)
            log.warn("Reached the maximum number of taxa: {}, results may be truncated", MAX_TAXA);

        return items;
    }

    // Build the final query string: ((batch1) OR (batch2) OR ...)
    String buildQuery(List<SpeciesListItem> items) {
        return batches(items).map(this::listBatchToString).collect(Collectors.joining(" OR "));
    }

    private String getQidForItems(List<SpeciesListItem> items) throws Exception {
        String formData = "q=" + URLEncoder.encode("(" + buildQuery(items) + ")", StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

        HttpRequest httpRequest = HttpRequest.newBuilder(new URI(biocacheUrl + "/ws/qid"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
    
}
