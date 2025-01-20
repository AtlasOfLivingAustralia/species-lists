package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.IngestProgressItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.Optional;

public interface IngestProgressMongoRepository extends MongoRepository<IngestProgressItem, String> {
  Optional<IngestProgressItem> findIngestProgressItemBySpeciesListId(String speciesListId);
  void deleteIngestProgressItemsByStartedBefore(Date started);
}
