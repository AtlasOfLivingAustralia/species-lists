package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.SpeciesList;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SpeciesListMongoRepository extends MongoRepository<SpeciesList, String> {

  void deleteById(String id);

  long count();

  long countSpeciesListByIsAuthoritative(boolean isAuthoritative);

  long countSpeciesListByIsPrivate(boolean isPrivate);

  @Query(value = "{'title': {$regex : ?0, $options: 'i'}}")
  Page<SpeciesList> findByTitleRegex(String regexString, Pageable pageable);

  Optional<SpeciesList> findByIdOrDataResourceUid(String speciesListID, String dataResourceUID);

  @Query(value = "{'isPrivate': ?0}")
  Page<SpeciesList> findAllByIsPrivate(Boolean isPrivate, Pageable pageable);
}
