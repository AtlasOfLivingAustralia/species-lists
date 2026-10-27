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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    // Lifetime of QIDs created for species lists: one week. Sent to biocache as
    // the maxage param (in milliseconds) - biocache deletes expired QIDs and
    // fails requests referencing them with HTTP 400, so the local scoped QID
    // cache below must never outlive this.
    public static final long QID_MAX_AGE_SECONDS = 604800;
    private static final long QID_MAX_AGE_MS = TimeUnit.SECONDS.toMillis(QID_MAX_AGE_SECONDS);

    // Sources reported to biocache when creating a QID, so QID volume from this
    // service can be attributed and cleaned independently
    public static final String SOURCE_UNFILTERED = "species-lists:full";
    public static final String SOURCE_FILTERED = "species-lists:scoped";

    // Cache of scoped QIDs: identical filter sets are common (repeat clicks), so
    // the item scan and biocache POST are skipped within the TTL. Cached entries
    // may be stale for up to the TTL if the list content changes. The TTL must
    // never exceed the biocache QID maxage (QID_MAX_AGE_SECONDS); larger values
    // are capped.
    private static final int MAX_CACHED_SCOPED_QIDS = 1000;
    private final ConcurrentHashMap<String, CachedQid> scopedQidCache = new ConcurrentHashMap<>();

    @Value("${qid.cache.ttl.seconds:604800}")
    private long scopedQidCacheTtlSeconds;

    private record CachedQid(String qid, long expiresAtEpochMs) {}

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
        return getQidForItems(collectUnfilteredItems(speciesListId), SOURCE_UNFILTERED, QID_MAX_AGE_MS);
    }

    /**
     * Generates a QID for a species list restricted to the items matching the given
     * filters. Uses the same Elasticsearch query as the single list page
     * (see SearchHelperService#searchSingleSpeciesList), so the resulting taxa set
     * matches exactly what a user sees when the filters are applied in the UI.
     *
     * <p>Scoped QIDs are cached by (list ID, filters) for {@code qid.cache.ttl.seconds}
     * (capped at the QID maxage) to avoid re-scanning the items and re-POSTing to
     * biocache for repeated clicks. The QID is created with a maxage of one week
     * (QID_MAX_AGE_SECONDS), so biocache deletes the stored query when it expires.
     */
    public String getQidForSpeciesList(SpeciesList speciesList, List<Filter> filters) throws Exception {
        String key = scopedCacheKey(speciesList.getId(), filters);
        long now = System.currentTimeMillis();

        CachedQid cached = scopedQidCache.get(key);
        if (cached != null && cached.expiresAtEpochMs() > now) {
            return cached.qid();
        }

        String qid = getQidForItems(collectFilteredItems(speciesList, filters), SOURCE_FILTERED, QID_MAX_AGE_MS);
        long ttlSeconds = Math.min(scopedQidCacheTtlSeconds, QID_MAX_AGE_SECONDS);
        scopedQidCache.put(key, new CachedQid(qid, now + TimeUnit.SECONDS.toMillis(ttlSeconds)));

        if (scopedQidCache.size() > MAX_CACHED_SCOPED_QIDS) {
            scopedQidCache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs() <= now);
        }

        return qid;
    }

    static String scopedCacheKey(String speciesListId, List<Filter> filters) {
        // Sorted so the same filter set in any order shares one cache entry
        String canonical = filters.stream()
                .map(filter -> filter.getKey() + "\u001f" + filter.getValue())
                .sorted()
                .collect(Collectors.joining("\u001e"));
        return speciesListId + ":" + sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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

    String formData(List<SpeciesListItem> items, String source) {
        return formData(items, source, QID_MAX_AGE_MS);
    }

    String formData(List<SpeciesListItem> items, String source, long maxAgeMs) {
        String formData = "q=" + URLEncoder.encode("(" + buildQuery(items) + ")", StandardCharsets.UTF_8)
                + "&source=" + source;
        if (maxAgeMs > 0) {
            formData += "&maxage=" + maxAgeMs;
        }
        return formData;
    }

    String getQidForItems(List<SpeciesListItem> items, String source, long maxAgeMs) throws Exception {
        String formData = formData(items, source, maxAgeMs);

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
