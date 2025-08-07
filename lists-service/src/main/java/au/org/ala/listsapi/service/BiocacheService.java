package au.org.ala.listsapi.service;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;

@Service
public class BiocacheService {
    @Autowired
    protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

    public static final Logger log = LoggerFactory.getLogger(BiocacheService.class);

    @Value("${biocache.api.url}")
    private String biocacheUrl;

    private final int maxBooleanClause = 1000;

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
        int page = 0;
        int pageSize = 200;
        List<String> batchQueries = new java.util.ArrayList<>();
        List<SpeciesListItem> batch;

        int maxBatches = 10; // 2000 terms total, with 200 per batch
        int batchCount = 0;
        do {
            if (batchCount >= maxBatches) {
                log.warn("Reached the maximum number of batches: {}", maxBatches);
                break;
            }

            batch = speciesListItemMongoRepository
                .findNextBatch(speciesListId, null, PageRequest.of(page, pageSize));
            if (batch != null && !batch.isEmpty()) {
                String batchQuery = listBatchToString(batch);
                batchQueries.add(batchQuery);
            }

            page++;
            batchCount++;
        } while (batch != null && batch.size() == pageSize);

        // Build the final query string: ((batch1) OR (batch2) OR ...)
        String finalQuery = batchQueries.stream()
                .collect(Collectors.joining(" OR "));
        String formData = "q=" + URLEncoder.encode("(" + finalQuery + ")", StandardCharsets.UTF_8);

        HttpRequest httpRequest = HttpRequest.newBuilder(new URI(biocacheUrl + "/ws/qid"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .build()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
    
}
