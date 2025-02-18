package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Service;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class AdminService {
  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
  @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
  @Autowired protected ElasticsearchOperations elasticsearchOperations;
  @Autowired protected MongoTemplate mongoTemplate;

  public void deleteDocs() {
    speciesListMongoRepository.deleteAll();
    speciesListItemMongoRepository.deleteAll();
  }

  public void deleteIndex() {
    elasticsearchOperations.indexOps(IndexCoordinates.of("species-lists")).delete();
  }

  public HashMap<String, List<IndexInfo>> getMongoIndexes() {
    HashMap<String, List<IndexInfo>> indexData = new HashMap<>();
    mongoTemplate
            .getCollectionNames()
            .forEach(collection -> indexData.put(collection, mongoTemplate.indexOps(collection).getIndexInfo()));

    return indexData;
  }
}
