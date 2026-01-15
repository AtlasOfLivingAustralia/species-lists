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
