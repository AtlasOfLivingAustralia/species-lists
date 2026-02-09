package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.SpeciesListItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeciesListItemRepository
    extends JpaRepository<SpeciesListItem, String>, JpaSpecificationExecutor<SpeciesListItem> {

  Page<SpeciesListItem> findBySpeciesListIDOrderByScientificNameAsc(
      String speciesListID, Pageable pageable);

  // For batch processing (keyset pagination)
  List<SpeciesListItem> findBySpeciesListIDOrderByIdAsc(String speciesListID, Pageable pageable);

  default List<SpeciesListItem> findFirstBatch(String speciesListId, Pageable pageable) {
    return findBySpeciesListIDOrderByIdAsc(speciesListId, pageable);
  }

  List<SpeciesListItem> findBySpeciesListIDAndIdGreaterThanOrderByIdAsc(
      String speciesListID, String lastId, Pageable pageable);

  default List<SpeciesListItem> findNextBatchAfter(
      String speciesListId, String lastId, Pageable pageable) {
    return findBySpeciesListIDAndIdGreaterThanOrderByIdAsc(speciesListId, lastId, pageable);
  }

  void deleteBySpeciesListID(String speciesListID);

  @org.springframework.data.jpa.repository.Query(
      value =
          "SELECT COUNT(DISTINCT classification ->> 'taxonConceptID') FROM species_list_item WHERE species_list_id = :speciesListID",
      nativeQuery = true)
  Long countDistinctTaxonConceptIDBySpeciesListID(
      @org.springframework.data.repository.query.Param("speciesListID") String speciesListID);
}
