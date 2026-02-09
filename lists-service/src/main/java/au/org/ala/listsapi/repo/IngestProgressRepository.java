package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.IngestProgressItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestProgressRepository extends JpaRepository<IngestProgressItem, String> {
  Optional<IngestProgressItem> findBySpeciesListID(String speciesListID);

  void deleteBySpeciesListID(String speciesListID);

  void deleteByStartedBefore(java.util.Date date);
}
