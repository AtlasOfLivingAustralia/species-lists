package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

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

    // Spy: the scoped QID cache tests stub the internal getQidForItems call
    @Spy
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

    private SpeciesList list(String id) {
        SpeciesList list = new SpeciesList();
        list.setId(id);
        return list;
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

    @Nested
    @DisplayName("formData")
    class FormDataTests {

        @Test
        void unfilteredItems_reportUnfilteredSource() {
            String formData = biocacheService.formData(List.of(itemWithLsid("lsid:1")), BiocacheService.SOURCE_UNFILTERED);

            assertTrue(formData.startsWith("q=%28"));
            assertTrue(formData.endsWith("&source=" + BiocacheService.SOURCE_UNFILTERED
                    + "&maxage=" + TimeUnit.SECONDS.toMillis(BiocacheService.QID_MAX_AGE_SECONDS)));
            // QIDs expire after one week on the biocache side
            assertEquals(TimeUnit.SECONDS.toMillis(604800), TimeUnit.SECONDS.toMillis(BiocacheService.QID_MAX_AGE_SECONDS));
        }

        @Test
        void filteredItems_reportFilteredSource() {
            String formData = biocacheService.formData(List.of(itemWithLsid("lsid:1")), BiocacheService.SOURCE_FILTERED);

            assertTrue(formData.endsWith("&source=" + BiocacheService.SOURCE_FILTERED
                    + "&maxage=" + TimeUnit.SECONDS.toMillis(BiocacheService.QID_MAX_AGE_SECONDS)));
        }

        @Test
        void scopedItems_carryMaxAgeInMilliseconds() {
            String formData = biocacheService.formData(List.of(itemWithLsid("lsid:1")), BiocacheService.SOURCE_FILTERED, 600_000);

            assertTrue(formData.endsWith("&source=" + BiocacheService.SOURCE_FILTERED + "&maxage=600000"));
        }

        @Test
        void nonPositiveMaxAge_isOmitted() {
            String formData = biocacheService.formData(List.of(itemWithLsid("lsid:1")), BiocacheService.SOURCE_FILTERED, 0);

            assertTrue(formData.indexOf("maxage") == -1);
        }
    }

    @Nested
    @DisplayName("getSpeciesListQuery")
    class GetSpeciesListQueryTests {

        private List<SpeciesListItem> items(int count) {
            List<SpeciesListItem> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                items.add(itemWithLsid("lsid:" + i));
            }
            return items;
        }

        // ~16 chars per item in the built query: 420 items exceed MAX_RAW_QUERY_LENGTH
        private static final int OVERSIZED_COUNT = 420;

        @Test
        void smallQuery_returnsRawQueryWithoutCreatingQid() throws Exception {
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(List.of(itemWithLsid("lsid:1")));

            Map<String, String> result = biocacheService.getSpeciesListQuery(list("list1"), null);

            assertEquals(Map.of("query", "(lsid:lsid:1)"), result);
        }

        @Test
        void oversizedQuery_fallsBackToQid() throws Exception {
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(items(OVERSIZED_COUNT));
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> result = biocacheService.getSpeciesListQuery(list("list1"), null);

            assertEquals(Map.of("qid", "qid-list1"), result);
        }

        @Test
        void emptyItems_fallBackToQid() throws Exception {
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(List.of());
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> result = biocacheService.getSpeciesListQuery(list("list1"), null);

            assertEquals(Map.of("qid", "qid-list1"), result);
        }
    }

    @Nested
    @DisplayName("scoped query cache")
    class ScopedQidCacheTests {

        // 420 items build a query past MAX_RAW_QUERY_LENGTH, forcing the QID path
        private static final int OVERSIZED_COUNT = 420;

        private List<SpeciesListItem> items(int count) {
            List<SpeciesListItem> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                items.add(itemWithLsid("lsid:" + i));
            }
            return items;
        }

        // Stubs consecutive pages of 200 items, ending with a short page
        private void stubFilteredItems(int count) {
            List<SpeciesListItem> all = items(count);
            int page = 0;
            for (int i = 0; i < all.size(); i += 200) {
                List<SpeciesListItem> batch = new ArrayList<>(all.subList(i, Math.min(i + 200, all.size())));
                when(searchHelperService.searchSingleSpeciesList(any(), eq(PageRequest.of(page, 200))))
                        .thenReturn(pageOf(batch, page, 200));
                page++;
            }
        }

        @Test
        void identicalFilters_areCachedWithinTtl() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 600L);
            List<Filter> filters = List.of(new Filter("classification.kingdom", "Animalia"));
            stubFilteredItems(OVERSIZED_COUNT);
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> result1 = biocacheService.getSpeciesListQuery(list("list1"), filters);
            Map<String, String> result2 = biocacheService.getSpeciesListQuery(list("list1"), filters);

            assertEquals(Map.of("qid", "qid-list1"), result1);
            assertEquals(result1, result2);
            // The item scan runs only once (3 pages): the second call is served from the cache
            verify(searchHelperService, times(3)).searchSingleSpeciesList(any(), any(Pageable.class));
        }

        @Test
        void differentFilters_getSeparateQids() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 600L);
            List<Filter> filtersA = List.of(new Filter("classification.kingdom", "Animalia"));
            List<Filter> filtersB = List.of(new Filter("classification.kingdom", "Plantae"));

            stubFilteredItems(OVERSIZED_COUNT);
            doReturn("qid-a").doReturn("qid-b").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> resultA = biocacheService.getSpeciesListQuery(list("list1"), filtersA);
            Map<String, String> resultB = biocacheService.getSpeciesListQuery(list("list1"), filtersB);

            assertNotEquals(resultA, resultB);
            // Two separate scans (3 pages each)
            verify(searchHelperService, times(6)).searchSingleSpeciesList(any(), any(Pageable.class));
        }

        @Test
        void expiredEntries_areRefetched() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 0L);
            List<Filter> filters = List.of(new Filter("classification.kingdom", "Animalia"));

            stubFilteredItems(OVERSIZED_COUNT);
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            biocacheService.getSpeciesListQuery(list("list1"), filters);
            biocacheService.getSpeciesListQuery(list("list1"), filters);

            // TTL of 0 means the entry is expired immediately: two scans (3 pages each)
            verify(searchHelperService, times(6)).searchSingleSpeciesList(any(), any(Pageable.class));
        }

        @Test
        void unfilteredQueries_areNotCached() throws Exception {
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(List.of(itemWithLsid("lsid:1")));

            Map<String, String> result1 = biocacheService.getSpeciesListQuery(list("list1"), null);
            Map<String, String> result2 = biocacheService.getSpeciesListQuery(list("list1"), null);

            assertEquals(result1, result2);
            assertEquals(Map.of("query", "(lsid:lsid:1)"), result1);
            verify(speciesListItemMongoRepository, times(2)).findNextBatch(eq("list1"), eq(null), any(Pageable.class));
        }

        @Test
        void sameFiltersInDifferentOrder_shareOneCacheEntry() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 600L);
            List<Filter> filtersA = List.of(
                    new Filter("classification.kingdom", "Animalia"),
                    new Filter("classification.family", "Fabaceae"));
            List<Filter> filtersB = List.of(
                    new Filter("classification.family", "Fabaceae"),
                    new Filter("classification.kingdom", "Animalia"));

            stubFilteredItems(OVERSIZED_COUNT);
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> resultA = biocacheService.getSpeciesListQuery(list("list1"), filtersA);
            Map<String, String> resultB = biocacheService.getSpeciesListQuery(list("list1"), filtersB);

            assertEquals(resultA, resultB);
            // One scan (3 pages) serves both orderings
            verify(searchHelperService, times(3)).searchSingleSpeciesList(any(), any(Pageable.class));
        }
    }
}
