package au.org.ala.listsapi.repo;

import au.org.ala.listsapi.model.SpeciesList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SpeciesListCustomRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    public Page<SpeciesList> findByMultipleExamples(SpeciesList exampleA, SpeciesList exampleB, Pageable pageable) {

        // Create ExampleMatcher for each "probe"
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withIgnoreCase()
                .withIgnoreNullValues()
                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);

        // Build Spring Data Example objects
        Example<SpeciesList> exA = Example.of(exampleA, matcher);
        Example<SpeciesList> exB = Example.of(exampleB, matcher);

        // Turn each Example into a Criteria object
        Criteria criteriaA = new Criteria().alike(exA);
        Criteria criteriaB = new Criteria().alike(exB);

        // Combine them using OR
        Criteria combined = new Criteria().orOperator(criteriaA, criteriaB);

        Query query = new Query(combined).with(pageable).with(Sort.by("_id"));

        // Execute the query
        List<SpeciesList> content = mongoTemplate.find(query, SpeciesList.class);

        // For total count (if needed for Page):
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), SpeciesList.class);

        return new PageImpl<>(content, pageable, total);
    }
}