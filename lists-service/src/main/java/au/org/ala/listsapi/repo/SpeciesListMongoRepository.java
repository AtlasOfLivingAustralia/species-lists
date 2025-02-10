package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.SpeciesList;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SpeciesListMongoRepository extends MongoRepository<SpeciesList, String> {

  void deleteById(String id);

  long count();

  Optional<SpeciesList> findByIdOrDataResourceUid(String speciesListID, String dataResourceUID);

  List<SpeciesList> findAllByDataResourceUidIsIn(Collection<String> dataResourceUIDs);
}
