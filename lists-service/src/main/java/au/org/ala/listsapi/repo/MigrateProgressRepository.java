package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.MigrateProgressItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrateProgressRepository extends JpaRepository<MigrateProgressItem, String> {}
