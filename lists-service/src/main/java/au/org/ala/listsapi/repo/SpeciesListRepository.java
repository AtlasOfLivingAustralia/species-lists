package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.SpeciesList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeciesListRepository
    extends JpaRepository<SpeciesList, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<SpeciesList> {

  Optional<SpeciesList> findByIdOrDataResourceUid(String id, String dataResourceUid);

  Optional<SpeciesList> findByDataResourceUid(String dataResourceUid);

  List<SpeciesList> findAllByDataResourceUidIsIn(Collection<String> dataResourceUIDs);

  @Query("SELECT s FROM SpeciesList s WHERE s.dataResourceUid IN ?1 OR s.id IN ?1")
  List<SpeciesList> findByDataResourceUidInOrIdIn(Collection<String> ids);

  @Query(
      value =
          "SELECT DISTINCT sl.* "
              + "FROM species_list sl "
              + "INNER JOIN species_list_item sli ON sl.id = sli.species_list_id "
              + "WHERE (sli.taxon_id = :guid OR sli.classification ->> 'taxonConceptID' = :guid) "
              + "AND ("
              + "    (:isAdmin = true)"
              + "    OR"
              + "    (:isPublicOnly = true AND sl.is_private = false)"
              + "    OR"
              + "    (sl.is_private = false OR sl.owner = :userId)"
              + ")",
      countQuery =
          "SELECT COUNT(DISTINCT sl.id) "
              + "FROM species_list sl "
              + "INNER JOIN species_list_item sli ON sl.id = sli.species_list_id "
              + "WHERE (sli.taxon_id = :guid OR sli.classification ->> 'taxonConceptID' = :guid) "
              + "AND ("
              + "    (:isAdmin = true)"
              + "    OR"
              + "    (:isPublicOnly = true AND sl.is_private = false)"
              + "    OR"
              + "    (sl.is_private = false OR sl.owner = :userId)"
              + ")",
      nativeQuery = true)
  Page<SpeciesList> findListsByTaxonGuid(
      @Param("guid") String guid,
      @Param("userId") String userId,
      @Param("isPublicOnly") boolean isPublicOnly,
      @Param("isAdmin") boolean isAdmin,
      Pageable pageable);
}
