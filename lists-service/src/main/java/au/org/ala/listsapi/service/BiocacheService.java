package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

@Service
public class BiocacheService {
  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

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

  private String listBatchToString (List<SpeciesListItem> listBatch) {
    return "(" + listBatch.stream().map((speciesListItem -> {
      Classification classification = speciesListItem.getClassification();

      if (classification != null && classification.getTaxonConceptID() != null) {
        return "lsid:" + classification.getTaxonConceptID();
      } else if (speciesListItem.getScientificName() != null) {
        return "raw_name:" + speciesListItem.getScientificName();
      }

      return null;
    })).filter(Objects::nonNull).collect(Collectors.joining(" OR ")) + ")";
  }

  public String getQidForSpeciesList(String speciesListId) throws Exception {
    List<SpeciesListItem> speciesListItems = speciesListItemMongoRepository
            .findNextBatch(speciesListId, null, PageRequest.of(0, 200));

    // Handle very long species lists
    Stream<String> queryBatches = batches(speciesListItems).map(this::listBatchToString);
    String query = queryBatches.collect(Collectors.joining(" OR "));

    String formData = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

    HttpRequest httpRequest =
            HttpRequest.newBuilder(new URI(biocacheUrl + "/ws/qid"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

    HttpResponse<String> response =
            HttpClient.newBuilder()
                    .proxy(ProxySelector.getDefault())
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

    return response.body();
  }
}
