package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatsService {
    public static final String SPECIES_LIST_ID = "speciesListID";
    @Autowired protected ElasticsearchOperations elasticsearchOperations;
    @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
    @Autowired protected MongoTemplate mongoTemplate;

    public long getListElasticRecordCount(String speciesListID) {
        NativeQueryBuilder builder = NativeQuery.builder();
        builder.withQuery(
                q ->
                        q.bool(
                                bq -> {
                                    bq.filter(f -> f.term(t -> t.field("speciesListID").value(speciesListID)));
                                    return bq;
                                }));

        // aggregation on species list ID
        builder.withAggregation(
                SPECIES_LIST_ID,
                Aggregation.of(a -> a.terms(ta -> ta.field(SPECIES_LIST_ID + ".keyword").size(10000))));

        Query aggQuery = builder.build();

        SearchHits<SpeciesListIndex> results =
                elasticsearchOperations.search(aggQuery, SpeciesListIndex.class);

        ElasticsearchAggregations agg = (ElasticsearchAggregations) results.getAggregations();

        long elasticCount = 0;
        if (agg != null && !agg.aggregations().isEmpty()) {
            List<StringTermsBucket> buckets = agg.aggregations().get(0).aggregation().getAggregate().sterms().buckets().array();

            if (!buckets.isEmpty()) {
                elasticCount = buckets.get(0).docCount();
            }
        }

        return elasticCount;
    }

    public long getListMongoRecordCount(String speciesListID) {
        SpeciesListItem listItem = new SpeciesListItem();
        listItem.setSpeciesListID(speciesListID);
        listItem.setMatchChecked(true);
        return speciesListItemMongoRepository.count(Example.of(listItem));
    }
}
