package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.Release;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseRepository extends JpaRepository<Release, String> {
  Page<Release> findBySpeciesListID(String speciesListID, Pageable pageable);
}
