package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.SpeciesListIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SpeciesListIndexElasticRepository
    extends ElasticsearchRepository<SpeciesListIndex, String> {

  @Query(value = "{'speciesListID' : $0}", delete = true)
  void deleteSpeciesListItemBySpeciesListID(String speciesListID);
}
