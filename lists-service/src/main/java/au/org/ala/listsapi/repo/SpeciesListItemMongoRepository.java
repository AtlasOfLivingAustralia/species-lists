package au.org.ala.listsapi.repo;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import au.org.ala.listsapi.model.SpeciesListItem;

public interface SpeciesListItemMongoRepository extends MongoRepository<SpeciesListItem, String> {
    Page<SpeciesListItem> findBySpeciesListIDOrderById(String speciesListID, Pageable pageable);

      /**
       * Fetches up to `batchSize` items for the given `speciesListId`.
       *
       * The OR condition handles two cases:
       *  1) lastId == null -> match everything (i.e. first batch).
       *  2) lastId != null -> match only _id > lastId.
       *
       * We combine them with "speciesListID = ?0 AND ($or: [...])".
     * 
     * Update: This appears to be slow in DocumentDB (AWS), with 
     * bulk taxon matching taking much longer than expected.
     * As DocumentDB's query optimizer doesn't handle $expr well, causing full collection scans.
     * 
     * So we have split this into two separate queries:
     *  - findFirstBatch() for the first batch (no lastId)
     *  - findNextBatchAfter() for subsequent batches (with lastId)
     *
     * @param speciesListId The species list ID
     * @param lastId The last _id from the previous batch (or null for first batch)
     * @param pageable Pageable object with the desired batch size
     * @return List of SpeciesListItem objects for the given batch
       */
      @Query(
              value = "{" +
                      "  'speciesListID': ?0," +
                      "  '$or': [" +
                      "    { '_id': { '$gt': ?1 } }," +
                      "    { '$expr': { '$eq': [ ?1, null ] } }" +
                      "  ]" +
                      "}",
              sort  = "{ '_id': 1 }" // sort ascending by _id
      )
      List<SpeciesListItem> findNextBatch(String speciesListId, ObjectId lastId, Pageable pageable);

    /**
     * Fetches up to `batchSize` items for the given list of `speciesListIds`.
     *
     * Uses offset-based pagination via the Pageable parameter.
     *
     * @param speciesListIds The list of species list IDs
     * @param queryString The query string to filter results (searches across common fields)
     * @param sortField The field to sort by
     * @param sortDirection The sort direction (1 for ascending, -1 for descending)
     * @param pageable Pageable object with the desired batch size and offset
     * @return List of SpeciesListItem objects for the given batch
     */
    @Query(value = """
        {
            'speciesListID': { '$in': ?0 },
            '$or': [
                { 'rawScientificName': { '$regex': ?1, '$options': 'i' } },
                { 'suppliedName': { '$regex': ?1, '$options': 'i' } },
                { 'vernacularName': { '$regex': ?1, '$options': 'i' } },
                { 'scientificName': { '$regex': ?1, '$options': 'i' } },
                { 'classification.phylum': { '$regex': ?1, '$options': 'i' } },
                { 'classification.classs': { '$regex': ?1, '$options': 'i' } },
                { 'classification.order': { '$regex': ?1, '$options': 'i' } },
                { 'classification.family': { '$regex': ?1, '$options': 'i' } },
                { 'classification.genus': { '$regex': ?1, '$options': 'i' } },
                { 'classification.scientificName': { '$regex': ?1, '$options': 'i' } },
                { 'classification.vernacularName': { '$regex': ?1, '$options': 'i' } },
                { 'properties.value': { '$regex': ?1, '$options': 'i' } }
            ]
        }
    """)
    Page<SpeciesListItem> findNextBatch(
            List<String> speciesListIds,
            String queryString,
            Pageable pageable
    );

    /**
     * For the first batch (no lastId)
     * Used by bulk operations like taxon matching for better performance
     *  
     * @param speciesListId
     * @param pageable
     * @return
     */
    @Query(value = "{ 'speciesListID': ?0 }", sort = "{ '_id': 1 }")
    List<SpeciesListItem> findFirstBatch(String speciesListId, Pageable pageable);
    
    /**
     * For subsequent batches (with lastId)
     * Used by bulk operations like taxon matching for better performance
     * 
     * @param speciesListId
     * @param lastId
     * @param pageable
     * @return
     */
    @Query(value = "{ 'speciesListID': ?0, '_id': { '$gt': ?1 } }", sort = "{ '_id': 1 }")
    List<SpeciesListItem> findNextBatchAfter(String speciesListId, ObjectId lastId, Pageable pageable);
    /**
     * For the first batch (no lastId)
     * Used by bulk operations like taxon matching for better performance
     *  
     * @param speciesListId
     * @param pageable
     * @return
     */
    @Query(value = "{ 'speciesListID': ?0 }", sort = "{ '_id': 1 }")
    List<SpeciesListItem> findFirstBatch(String speciesListId, Pageable pageable);

    /**
     * For subsequent batches (with lastId)
     * Used by bulk operations like taxon matching for better performance
     * 
     * @param speciesListId
     * @param lastId
     * @param pageable
     * @return
     */
    @Query(value = "{ 'speciesListID': ?0, '_id': { '$gt': ?1 } }", sort = "{ '_id': 1 }")
    List<SpeciesListItem> findNextBatchAfter(String speciesListId, ObjectId lastId, Pageable pageable);

      void deleteBySpeciesListID(String speciesListID);
}
