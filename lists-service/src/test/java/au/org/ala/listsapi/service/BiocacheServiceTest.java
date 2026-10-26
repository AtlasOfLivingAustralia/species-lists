package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;

@ExtendWith(MockitoExtension.class)
class BiocacheServiceTest {

    @Mock
    private SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @Mock
    private SearchHelperService searchHelperService;

    @InjectMocks
    private BiocacheService biocacheService;

    private SpeciesListItem itemWithLsid(String lsid) {
        SpeciesListItem item = new SpeciesListItem();
        Classification classification = new Classification();
        classification.setTaxonConceptID(lsid);
        classification.setScientificName("Scientific " + lsid);
        item.setClassification(classification);
        item.setScientificName("Scientific " + lsid);
        return item;
    }

    private SpeciesListItem itemWithRawName(String name) {
        SpeciesListItem item = new SpeciesListItem();
        item.setScientificName(name);
        return item;
    }

    private PageImpl<SpeciesListItem> pageOf(List<SpeciesListItem> items, int page, int pageSize) {
        return new PageImpl<>(items, PageRequest.of(page, pageSize), items.size());
    }

    @Nested
    @DisplayName("buildQuery")
    class BuildQueryTests {

        @Test
        void singleBatch_isWrappedInOneParenthesisedGroup() {
            List<SpeciesListItem> items = List.of(itemWithLsid("lsid:1"), itemWithRawName("Unclassified sp."));

            String query = biocacheService.buildQuery(items);

            assertEquals("(lsid:lsid:1 OR raw_scientificName:\"Unclassified sp.\")", query);
        }

        @Test
        void largerThanMaxBooleanClause_splitsIntoOrGroups() {
            List<SpeciesListItem> items = new ArrayList<>();
            for (int i = 0; i < 1001; i++) {
                items.add(itemWithLsid("lsid:" + i));
            }

            String query = biocacheService.buildQuery(items);

            // 1001 items -> one full group of 1000 plus a second group with the remainder
            String[] groups = query.split("\\) OR \\(");
            assertEquals(2, groups.length);
            assertTrue(groups[0].startsWith("(lsid:lsid:0"));
            assertTrue(groups[0].endsWith("lsid:lsid:999"));
            assertEquals("lsid:lsid:1000)", groups[1]);
        }

        @Test
        void prefersLsidOverScientificName() {
            String query = biocacheService.buildQuery(List.of(itemWithLsid("lsid:42")));
            assertEquals("(lsid:lsid:42)", query);
        }

        @Test
        void itemsWithoutTaxonOrName_areSkipped() {
            SpeciesListItem empty = new SpeciesListItem();
            String query = biocacheService.buildQuery(List.of(itemWithLsid("lsid:1"), empty, itemWithRawName("Raw name")));
            assertEquals("(lsid:lsid:1 OR raw_scientificName:\"Raw name\")", query);
        }

        @Test
        void emptyItems_produceEmptyGroup() {
            // The surrounding parens are added when the q parameter is built
            assertEquals("", biocacheService.buildQuery(List.of()));
        }
    }

    @Nested
    @DisplayName("collectUnfilteredItems")
    class CollectUnfilteredItemsTests {

        @Test
        void pagesUntilShortPage() {
            List<SpeciesListItem> fullPage = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                fullPage.add(itemWithLsid("lsid:" + i));
            }
            List<SpeciesListItem> shortPage = List.of(itemWithLsid("lsid:last"));

            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(fullPage);
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(1, 200))))
                    .thenReturn(shortPage);

            List<SpeciesListItem> items = biocacheService.collectUnfilteredItems("list1");

            assertEquals(201, items.size());
            verify(speciesListItemMongoRepository, times(2)).findNextBatch(eq("list1"), eq(null), any(Pageable.class));
        }

        @Test
        void stopsAtMaximumTaxa() {
            List<SpeciesListItem> fullPage = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                fullPage.add(itemWithLsid("lsid:" + i));
            }
            // Every page is full: 10 pages x 200 = 2000 max
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), any(Pageable.class)))
                    .thenReturn(fullPage);

            List<SpeciesListItem> items = biocacheService.collectUnfilteredItems("list1");

            assertEquals(2000, items.size());
            verify(speciesListItemMongoRepository, times(10)).findNextBatch(eq("list1"), eq(null), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("collectFilteredItems")
    class CollectFilteredItemsTests {

        @Test
        void pagesThroughSearchHelperUntilShortPage() {
            SpeciesList list = new SpeciesList();
            list.setId("list1");
            List<Filter> filters = List.of(new Filter("classification.kingdom", "Animalia"));

            List<SpeciesListItem> fullPage = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                fullPage.add(itemWithLsid("lsid:" + i));
            }
            List<SpeciesListItem> shortPage = List.of(itemWithLsid("lsid:last"));

            when(searchHelperService.searchSingleSpeciesList(any(), eq(PageRequest.of(0, 200))))
                    .thenReturn(pageOf(fullPage, 0, 200));
            when(searchHelperService.searchSingleSpeciesList(any(), eq(PageRequest.of(1, 200))))
                    .thenReturn(pageOf(shortPage, 1, 200));

            List<SpeciesListItem> items = biocacheService.collectFilteredItems(list, filters);

            assertEquals(201, items.size());
            verify(searchHelperService, times(2)).searchSingleSpeciesList(any(), any(Pageable.class));
        }

        @Test
        void stopsAtMaximumTaxa() {
            SpeciesList list = new SpeciesList();
            list.setId("list1");
            List<Filter> filters = List.of(new Filter("classification.kingdom", "Animalia"));

            List<SpeciesListItem> fullPage = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                fullPage.add(itemWithLsid("lsid:" + i));
            }
            when(searchHelperService.searchSingleSpeciesList(any(), any(Pageable.class)))
                    .thenReturn(pageOf(fullPage, 0, 200));

            List<SpeciesListItem> items = biocacheService.collectFilteredItems(list, filters);

            assertEquals(2000, items.size());
        }

        @Test
        void emptyResult_returnsEmptyList() {
            SpeciesList list = new SpeciesList();
            list.setId("list1");

            when(searchHelperService.searchSingleSpeciesList(any(), eq(PageRequest.of(0, 200))))
                    .thenReturn(pageOf(List.of(), 0, 200));

            List<SpeciesListItem> items = biocacheService.collectFilteredItems(list, List.of());

            assertTrue(items.isEmpty());
            // A single empty page is enough - no further pages are requested
            verify(searchHelperService, times(1)).searchSingleSpeciesList(any(), any(Pageable.class));
            verifyNoInteractions(speciesListItemMongoRepository);
        }
    }
}
