package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.IngestProgressItem;
import au.org.ala.listsapi.model.MigrateProgressItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.Optional;

public interface MigrateProgressMongoRepository extends MongoRepository<MigrateProgressItem, String> {
}
