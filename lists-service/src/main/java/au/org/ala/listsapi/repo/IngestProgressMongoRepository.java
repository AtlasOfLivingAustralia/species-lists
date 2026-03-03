package au.org.ala.listsapi.repo;

import java.util.Date;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import au.org.ala.listsapi.model.IngestProgressItem;

public interface IngestProgressMongoRepository extends MongoRepository<IngestProgressItem, String> {
    Optional<IngestProgressItem> findIngestProgressItemBySpeciesListID(String speciesListID);
    void deleteIngestProgressItemsByStartedBefore(Date started);

    void deleteIngestProgressItemBySpeciesListID(String speciesListID);
}
