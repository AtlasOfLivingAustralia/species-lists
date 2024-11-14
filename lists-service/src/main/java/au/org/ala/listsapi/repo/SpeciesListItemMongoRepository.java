package au.org.ala.listsapi.repo;

import java.util.List;
import au.org.ala.listsapi.model.SpeciesListItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpeciesListItemMongoRepository extends MongoRepository<SpeciesListItem, String> {

  Page<SpeciesListItem> findBySpeciesListID(String speciesListID, Pageable pageable);
  List<SpeciesListItem> findAllBySpeciesListID(String speciesListID);

  void deleteBySpeciesListID(String speciesListID);
}
