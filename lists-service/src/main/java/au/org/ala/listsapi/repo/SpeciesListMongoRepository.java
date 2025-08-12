package au.org.ala.listsapi.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import au.org.ala.listsapi.model.SpeciesList;

public interface SpeciesListMongoRepository extends MongoRepository<SpeciesList, String> {

    void deleteById(String id);

    long count();

    Optional<SpeciesList> findByIdOrDataResourceUid(String speciesListID, String dataResourceUid);

    Optional<SpeciesList> findByDataResourceUid(String dataResourceUid);

    List<SpeciesList> findAllByDataResourceUidIsIn(Collection<String> dataResourceUIDs);

    List<SpeciesList> findAllByDataResourceUidIsInOrIdIsIn(Collection<String> dataResourceUid, Collection<String> id);
}
