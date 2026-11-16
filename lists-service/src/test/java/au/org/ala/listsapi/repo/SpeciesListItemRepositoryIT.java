package au.org.ala.listsapi.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.support.AbstractMongoIT;

/**
 * The list item queries against a real MongoDB. Three of them are written as JSON rather than
 * derived from the method name, one of those with a SpEL expression, so MongoDB is the only place
 * they can be shown to work: the batch queries that drive taxon matching, and the search across
 * names, classification and supplied properties.
 */
class SpeciesListItemRepositoryIT extends AbstractMongoIT {

    @Autowired private SpeciesListItemMongoRepository itemRepository;

    @BeforeEach
    void clearItems() {
        itemRepository.deleteAll();
    }

    private SpeciesListItem saved(String listId, String suppliedName) {
        return itemRepository.save(
                SpeciesListItem.builder().speciesListID(listId).suppliedName(suppliedName).build());
    }

    @Test
    void findBySpeciesListIDOrderById_pagesOneListOnly() {
        for (int i = 0; i < 5; i++) {
            saved("dr1", "Name " + i);
        }
        saved("dr2", "Other list");

        Page<SpeciesListItem> page = itemRepository.findBySpeciesListIDOrderById("dr1", PageRequest.of(0, 2));

        assertEquals(5, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertTrue(page.getContent().stream().allMatch(item -> "dr1".equals(item.getSpeciesListID())));
    }

    @Test
    void findNextBatch_withoutALastIdReturnsTheFirstBatch() {
        for (int i = 0; i < 4; i++) {
            saved("dr1", "Name " + i);
        }

        // lastId null takes the $expr branch, which is the "everything" case
        List<SpeciesListItem> batch = itemRepository.findNextBatch("dr1", null, PageRequest.of(0, 3));

        assertEquals(3, batch.size());
    }

    @Test
    void findNextBatch_afterALastIdSkipsWhatWasSeen() {
        List<ObjectId> ids =
                List.of(
                        saved("dr1", "First").getId(),
                        saved("dr1", "Second").getId(),
                        saved("dr1", "Third").getId());

        List<SpeciesListItem> batch =
                itemRepository.findNextBatch("dr1", ids.get(0), PageRequest.of(0, 10));

        assertEquals(2, batch.size());
        assertTrue(batch.stream().noneMatch(item -> item.getId().equals(ids.get(0))));
    }

    @Test
    void findFirstBatchAndFindNextBatchAfter_walkTheListInIdOrder() {
        for (int i = 0; i < 5; i++) {
            saved("dr1", "Name " + i);
        }

        // The pair DocumentDB needs instead of the $expr query: first batch, then everything after
        // the last id of the previous one
        List<SpeciesListItem> first = itemRepository.findFirstBatch("dr1", PageRequest.of(0, 2));
        assertEquals(2, first.size());

        List<SpeciesListItem> second =
                itemRepository.findNextBatchAfter("dr1", first.get(1).getId(), PageRequest.of(0, 2));

        assertEquals(2, second.size());
        assertTrue(second.get(0).getId().compareTo(first.get(1).getId()) > 0);
    }

    @Test
    void findNextBatch_withNoListIdsSearchesEveryList() {
        saved("dr1", "Acacia dealbata");
        saved("dr2", "Acacia melanoxylon");
        saved("dr3", "Eucalyptus regnans");

        // The SpEL in the query turns an empty list into { $exists: true }, i.e. no list filter
        Page<SpeciesListItem> page =
                itemRepository.findNextBatch(List.of(), "acacia", PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
    }

    @Test
    void findNextBatch_withListIdsRestrictsToThem() {
        saved("dr1", "Acacia dealbata");
        saved("dr2", "Acacia melanoxylon");

        Page<SpeciesListItem> page =
                itemRepository.findNextBatch(List.of("dr1"), "acacia", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("dr1", page.getContent().get(0).getSpeciesListID());
    }

    @Test
    void findNextBatch_searchesClassificationAndSuppliedProperties() {
        itemRepository.save(
                SpeciesListItem.builder()
                        .speciesListID("dr1")
                        .suppliedName("Wattle")
                        .classification(Classification.builder().family("Fabaceae").build())
                        .build());
        itemRepository.save(
                SpeciesListItem.builder()
                        .speciesListID("dr1")
                        .suppliedName("Gum")
                        .properties(List.of(KeyValue.builder().key("status").value("Endangered").build()))
                        .build());
        saved("dr1", "Neither");

        // A nested classification field and a property value, both reached by the same query
        assertEquals(
                1, itemRepository.findNextBatch(List.of("dr1"), "fabaceae", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(
                1,
                itemRepository
                        .findNextBatch(List.of("dr1"), "endangered", PageRequest.of(0, 10))
                        .getTotalElements());
    }

    @Test
    void deleteBySpeciesListID_leavesOtherListsAlone() {
        saved("dr1", "Goes");
        saved("dr1", "Also goes");
        saved("dr2", "Stays");

        itemRepository.deleteBySpeciesListID("dr1");

        assertEquals(1, itemRepository.count());
        assertEquals(
                0, itemRepository.findBySpeciesListIDOrderById("dr1", PageRequest.of(0, 10)).getTotalElements());
    }
}
