package au.org.ala.listsapi.repo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import au.org.ala.listsapi.model.SpeciesList;

@Repository
public class SpeciesListCustomRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    public Page<SpeciesList> findByExample(SpeciesList example, String searchQuery, Pageable pageable) {
        return findByCriteria(new Criteria().alike(Example.of(example, matcher())), searchQuery, pageable);
    }

    public Page<SpeciesList> findByMultipleExamples(SpeciesList exampleA, SpeciesList exampleB, Pageable pageable) {
        return findByMultipleExamples(exampleA, exampleB, null, pageable);
    }

    public Page<SpeciesList> findByMultipleExamples(
            SpeciesList exampleA, SpeciesList exampleB, String searchQuery, Pageable pageable) {

        Criteria criteriaA = new Criteria().alike(Example.of(exampleA, matcher()));
        Criteria criteriaB = new Criteria().alike(Example.of(exampleB, matcher()));
        Criteria combined = new Criteria().orOperator(criteriaA, criteriaB);
        return findByCriteria(combined, searchQuery, pageable);
    }

    private Page<SpeciesList> findByCriteria(Criteria baseCriteria, String searchQuery, Pageable pageable) {
        Criteria criteria = baseCriteria;
        if (searchQuery != null && !searchQuery.isBlank()) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(searchQuery),
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            criteria = new Criteria().andOperator(
                    baseCriteria,
                    new Criteria().orOperator(
                            Criteria.where("title").regex(pattern),
                            Criteria.where("description").regex(pattern)));
        }

        Query query = new Query(criteria).with(pageable).with(Sort.by("_id"));
        List<SpeciesList> content = mongoTemplate.find(query, SpeciesList.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), SpeciesList.class);
        return new PageImpl<>(content, pageable, total);
    }

    private ExampleMatcher matcher() {
        return ExampleMatcher.matching()
                .withIgnoreCase()
                .withIgnoreNullValues()
                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);
    }
}
