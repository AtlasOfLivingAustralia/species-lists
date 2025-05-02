/*
 * Copyright (C) 2025 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.ws.security.profile.AlaUserProfile;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class MongoUtils {
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired protected AuthUtils authUtils;
    @Autowired protected ElasticsearchOperations elasticsearchOperations;

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

    public List<SpeciesListItem> fetchSpeciesListItems(String guids, @Nullable String speciesListIDs, int page, int pageSize, Principal principal) throws Exception {
        AlaUserProfile profile = authUtils.getUserProfile(principal);
        List<FieldValue> GUIDs = Arrays.stream(guids.split(",")).map(FieldValue::of).toList();
        List<FieldValue> listIDs = speciesListIDs != null ?
                Arrays.stream(speciesListIDs.split(",")).map(FieldValue::of).toList() : null;

        if (page < 1 || (page * pageSize) > 10000) {
            return new ArrayList<>();
        }

        Pageable pageableRequest = PageRequest.of(page - 1, pageSize);
        NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageableRequest);
        builder.withQuery(
                q ->
                        q.bool(
                                bq -> {
                                    bq.filter(f -> f.terms(t -> t.field("classification.taxonConceptID.keyword")
                                            .terms(ta -> ta.value(GUIDs))));

                                    if (listIDs != null) {
                                        bq.filter(f -> f.bool(b -> b
                                                .should(s -> s.terms(t -> t.field("speciesListID.keyword").terms(ta -> ta.value(listIDs))))
                                                .should(s -> s.terms(t -> t.field("dataResourceUid.keyword").terms(ta -> ta.value(listIDs))))
                                        ));
                                    }

                                    // If the user is not an admin, only query their private lists, and all other public lists
                                    if (!authUtils.isAuthenticated(principal)) {
                                        bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
                                    } else if (!authUtils.hasAdminRole(profile)) {
                                        bq.filter(f -> f.bool(b -> b
                                                .should(s -> s.bool(b2 -> b2
                                                        .must(m -> m.term(t -> t.field("owner").value(profile.getUserId())))
                                                        .must(m -> m.term(t -> t.field("isPrivate").value(true)))
                                                ))
                                                .should(s -> s.term(t -> t.field("isPrivate").value(false)))
                                        ));
                                    }

                                    return bq;
                                }));

        NativeQuery query = builder.build();
        query.setPageable(pageableRequest);
        SearchHits<SpeciesListIndex> results =
                elasticsearchOperations.search(
                        query, SpeciesListIndex.class, IndexCoordinates.of("species-lists"));

        return ElasticUtils.convertList((List<SpeciesListIndex>) SearchHitSupport.unwrapSearchHits(results));
    }
}
