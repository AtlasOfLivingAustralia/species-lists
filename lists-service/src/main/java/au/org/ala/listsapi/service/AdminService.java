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
package au.org.ala.listsapi.service;

import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Service;

import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;

@Service
public class AdminService {
    @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;
    @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
    @Autowired protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
    @Autowired protected ElasticsearchOperations elasticsearchOperations;
    @Autowired protected MongoTemplate mongoTemplate;

    public void deleteDocs() {
        speciesListMongoRepository.deleteAll();
        speciesListItemMongoRepository.deleteAll();
    }

    public void deleteIndex() {
        elasticsearchOperations.indexOps(IndexCoordinates.of("species-lists")).delete();
    }

    public HashMap<String, List<IndexInfo>> getMongoIndexes() {
        HashMap<String, List<IndexInfo>> indexData = new HashMap<>();
        mongoTemplate
                .getCollectionNames()
                .forEach(collection -> indexData.put(collection, mongoTemplate.indexOps(collection).getIndexInfo()));

        return indexData;
    }
}
