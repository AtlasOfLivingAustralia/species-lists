package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.Release;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReleaseMongoRepository extends MongoRepository<Release, String> {

  Page<Release> findBySpeciesListID(String speciesListID, Pageable pageable);
}
