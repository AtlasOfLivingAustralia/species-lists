package au.org.ala.listsapi.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.support.AbstractElasticIT;
import au.org.ala.listsapi.util.ElasticsearchQueryBuilder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;

/**
 * The search index against a real Elasticsearch: the index built from the project's own
 * settings.json and mappings.json, and the queries {@link ElasticsearchQueryBuilder} writes.
 *
 * <p>Two things here can only be shown against a server. The index has to be created from our
 * mapping, with the Elasticsearch 9 client talking to the 8.17 server through the compatibility
 * headers {@link au.org.ala.listsapi.config.Config} rewrites. And the rules that keep a private list
 * out of someone else's results are a query, so what they do depends on how Elasticsearch reads it.
 */
class SpeciesListIndexElasticIT extends AbstractElasticIT {

    @Autowired private ElasticsearchOperations elasticsearchOperations;

    @Autowired private SpeciesListIndexElasticRepository indexRepository;

    @BeforeEach
    void emptyIndex() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(SpeciesListIndex.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();
    }

    private SpeciesListIndex indexed(String id, String listId, String name, boolean isPrivate, String owner) {
        SpeciesListIndex document =
                SpeciesListIndex.builder()
                        .id(id)
                        .speciesListID(listId)
                        .speciesListName("List " + listId)
                        .scientificName(name)
                        .suppliedName(name)
                        .isPrivate(isPrivate)
                        .owner(owner)
                        .build();
        return elasticsearchOperations.save(document);
    }

    private void refresh() {
        elasticsearchOperations.indexOps(SpeciesListIndex.class).refresh();
    }

    private SearchHits<SpeciesListIndex> search(BoolQuery.Builder query) {
        return elasticsearchOperations.search(
                NativeQuery.builder().withQuery(q -> q.bool(query.build())).build(), SpeciesListIndex.class);
    }

    @Test
    void documentRoundTripsThroughTheProjectsMapping() {
        elasticsearchOperations.save(
                SpeciesListIndex.builder()
                        .id("item-1")
                        .speciesListID("dr1")
                        .speciesListName("Wattles")
                        .scientificName("Acacia dealbata")
                        .suppliedName("Silver Wattle")
                        .classification(Classification.builder().family("Fabaceae").rank("species").build())
                        .properties(List.of(KeyValue.builder().key("status").value("Least concern").build()))
                        .isPrivate(false)
                        .build());

        SpeciesListIndex found = elasticsearchOperations.get("item-1", SpeciesListIndex.class);

        assertNotNull(found);
        assertEquals("Acacia dealbata", found.getScientificName());
        assertEquals("Fabaceae", found.getClassification().getFamily());
        assertEquals("Least concern", found.getProperties().get(0).getValue());
    }

    @Test
    void searchQueryMatchesThroughTheCopyToAllField() {
        indexed("item-1", "dr1", "Acacia dealbata", false, "alice");
        indexed("item-2", "dr1", "Eucalyptus regnans", false, "alice");
        refresh();

        // The query searches the "all" field, which only holds anything because the mapping copies
        // the name fields into it
        SearchHits<SpeciesListIndex> hits =
                search(
                        ElasticsearchQueryBuilder.buildQuery(
                                "acacia", (String) null, null, false, null, null, new BoolQuery.Builder()));

        assertEquals(1, hits.getTotalHits());
        assertEquals("item-1", hits.getSearchHit(0).getId());
    }

    @Test
    void anonymousSearchHidesPrivateLists() {
        indexed("public-1", "dr1", "Acacia dealbata", false, "alice");
        indexed("private-1", "dr2", "Acacia melanoxylon", true, "alice");
        refresh();

        // No user and not an admin: the query has to filter isPrivate = false
        SearchHits<SpeciesListIndex> hits =
                search(
                        ElasticsearchQueryBuilder.buildQuery(
                                null, (String) null, null, false, null, null, new BoolQuery.Builder()));

        assertEquals(1, hits.getTotalHits());
        assertEquals("public-1", hits.getSearchHit(0).getId());
    }

    @Test
    void privateSearchReturnsOnlyTheUsersOwnLists() {
        indexed("alice-private", "dr1", "Acacia dealbata", true, "alice");
        indexed("bob-private", "dr2", "Acacia melanoxylon", true, "bob");
        indexed("public", "dr3", "Acacia pycnantha", false, "bob");
        refresh();

        SearchHits<SpeciesListIndex> hits =
                search(
                        ElasticsearchQueryBuilder.buildQuery(
                                null, (String) null, "alice", false, true, null, new BoolQuery.Builder()));

        assertEquals(1, hits.getTotalHits());
        assertEquals("alice-private", hits.getSearchHit(0).getId());
    }

    @Test
    void adminSearchSeesPrivateListsOfOtherUsers() {
        indexed("public-1", "dr1", "Acacia dealbata", false, "alice");
        indexed("private-1", "dr2", "Acacia melanoxylon", true, "bob");
        refresh();

        SearchHits<SpeciesListIndex> hits =
                search(
                        ElasticsearchQueryBuilder.buildQuery(
                                null, (String) null, null, true, null, null, new BoolQuery.Builder()));

        assertEquals(2, hits.getTotalHits());
    }

    @Test
    void searchCanBeRestrictedToOneList() {
        indexed("item-1", "dr1", "Acacia dealbata", false, "alice");
        indexed("item-2", "dr2", "Acacia melanoxylon", false, "alice");
        refresh();

        SearchHits<SpeciesListIndex> hits =
                search(
                        ElasticsearchQueryBuilder.buildQuery(
                                null, "dr2", null, false, null, null, new BoolQuery.Builder()));

        assertEquals(1, hits.getTotalHits());
        assertEquals("item-2", hits.getSearchHit(0).getId());
    }

    @Test
    void deleteBySpeciesListIDRemovesOnlyThatListsItems() {
        indexed("item-1", "dr1", "Acacia dealbata", false, "alice");
        indexed("item-2", "dr1", "Acacia melanoxylon", false, "alice");
        indexed("item-3", "dr2", "Eucalyptus regnans", false, "alice");
        refresh();

        indexRepository.deleteSpeciesListItemBySpeciesListID("dr1");
        refresh();

        assertEquals(1, indexRepository.count());
        assertTrue(indexRepository.findById("item-3").isPresent());
    }
}
