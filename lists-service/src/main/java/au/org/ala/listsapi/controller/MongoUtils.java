package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.SpeciesListItem;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MongoUtils {
    @Autowired private MongoTemplate mongoTemplate;

    public BulkWriteResult speciesListItemsBulkUpdate(List<SpeciesListItem> items, List<String> keys) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, SpeciesListItem.class);
        for (SpeciesListItem item : items) {
            // Build an upsert or replace operation based on unique identifier
            Query query = new Query(Criteria.where("_id").is(item.getId()));
            Update update = new Update();
            keys.forEach(key -> update.set(key, item.getPropFromKey(key)));

            bulkOps.upsert(query, update);
        }

        // Execute the bulk operation
        return bulkOps.execute();
    }

    public BulkWriteResult speciesListItemsBulkSave(List<SpeciesListItem> items) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, SpeciesListItem.class);
        bulkOps.insert(items);

        // Execute the bulk operation
        return bulkOps.execute();
    }
}
