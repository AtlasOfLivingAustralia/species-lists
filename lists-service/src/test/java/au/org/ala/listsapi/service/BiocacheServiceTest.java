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

import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setDefaults() {
        // @Value fields have no defaults without a Spring context
        ReflectionTestUtils.setField(biocacheService, "qidMaxAgeSeconds", 604800L);
        ReflectionTestUtils.setField(biocacheService, "maxRawQueryLength", 6000);
        ReflectionTestUtils.setField(biocacheService, "maxCachedScopedQids", 1000);
        ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 604800L);
    }

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
                    + "&maxage=" + TimeUnit.SECONDS.toMillis(604800)));
        }

        @Test
        void filteredItems_reportFilteredSource() {
            String formData = biocacheService.formData(List.of(itemWithLsid("lsid:1")), BiocacheService.SOURCE_FILTERED);

            assertTrue(formData.endsWith("&source=" + BiocacheService.SOURCE_FILTERED
                    + "&maxage=" + TimeUnit.SECONDS.toMillis(604800)));
        }

        @Test
        void disabledMaxAge_isOmitted() {
            ReflectionTestUtils.setField(biocacheService, "qidMaxAgeSeconds", -1L);

            String formData = biocacheService.formData(List.of(itemWithLsid("lsid:1")), BiocacheService.SOURCE_FILTERED);

            assertTrue(formData.indexOf("maxage") == -1);
        }

        @Test
        void customMaxAge_isUsedInFormData() {
            ReflectionTestUtils.setField(biocacheService, "qidMaxAgeSeconds", 3600L);

            String formData = biocacheService.formData(List.of(itemWithLsid("lsid:1")), BiocacheService.SOURCE_FILTERED);

            assertTrue(formData.endsWith("&maxage=3600000"));
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

        @Test
        void smallQuery_returnsRawQueryWithoutCreatingQid() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "maxRawQueryLength", 100000);
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(items(2));

            Map<String, String> result = biocacheService.getSpeciesListQuery(list("list1"), null);

            assertEquals(Map.of("query", "(lsid:lsid:0 OR lsid:lsid:1)"), result);
        }

        @Test
        void oversizedQuery_fallsBackToQid() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "maxRawQueryLength", 1);
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(items(2));
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
    @DisplayName("query result cache")
    class ScopedQidCacheTests {

        private List<SpeciesListItem> items(int count) {
            List<SpeciesListItem> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                items.add(itemWithLsid("lsid:" + i));
            }
            return items;
        }

        private List<SpeciesListItem> filteredItems(int count) {
            when(searchHelperService.searchSingleSpeciesList(any(), eq(PageRequest.of(0, 200))))
                    .thenReturn(pageOf(items(count), 0, 200));
            return items(count);
        }

        @Test
        void identicalFilters_areCachedWithinTtl() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 600L);
            // Force the QID path regardless of query size
            ReflectionTestUtils.setField(biocacheService, "maxRawQueryLength", 1);
            List<Filter> filters = List.of(new Filter("classification.kingdom", "Animalia"));
            filteredItems(2);
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> result1 = biocacheService.getSpeciesListQuery(list("list1"), filters);
            Map<String, String> result2 = biocacheService.getSpeciesListQuery(list("list1"), filters);

            assertEquals(Map.of("qid", "qid-list1"), result1);
            assertEquals(result1, result2);
            // The item scan runs only once: the second call is served from the cache
            verify(searchHelperService, times(1)).searchSingleSpeciesList(any(), any(Pageable.class));
        }

        @Test
        void differentFilters_getSeparateQids() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 600L);
            ReflectionTestUtils.setField(biocacheService, "maxRawQueryLength", 1);
            List<Filter> filtersA = List.of(new Filter("classification.kingdom", "Animalia"));
            List<Filter> filtersB = List.of(new Filter("classification.kingdom", "Plantae"));

            when(searchHelperService.searchSingleSpeciesList(any(), any(Pageable.class)))
                    .thenReturn(pageOf(items(1), 0, 200));
            doReturn("qid-a").doReturn("qid-b").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> resultA = biocacheService.getSpeciesListQuery(list("list1"), filtersA);
            Map<String, String> resultB = biocacheService.getSpeciesListQuery(list("list1"), filtersB);

            assertNotEquals(resultA, resultB);
            verify(searchHelperService, times(2)).searchSingleSpeciesList(any(), any(Pageable.class));
        }

        @Test
        void expiredEntries_areRefetched() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 0L);
            ReflectionTestUtils.setField(biocacheService, "maxRawQueryLength", 1);
            List<Filter> filters = List.of(new Filter("classification.kingdom", "Animalia"));

            when(searchHelperService.searchSingleSpeciesList(any(), any(Pageable.class)))
                    .thenReturn(pageOf(items(1), 0, 200));
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            biocacheService.getSpeciesListQuery(list("list1"), filters);
            biocacheService.getSpeciesListQuery(list("list1"), filters);

            // TTL of 0 means the entry is expired immediately
            verify(searchHelperService, times(2)).searchSingleSpeciesList(any(), any(Pageable.class));
        }

        @Test
        void unfilteredQueries_areCached() throws Exception {
            when(speciesListItemMongoRepository.findNextBatch(eq("list1"), eq(null), eq(PageRequest.of(0, 200))))
                    .thenReturn(List.of(itemWithLsid("lsid:1")));

            Map<String, String> result1 = biocacheService.getSpeciesListQuery(list("list1"), null);
            Map<String, String> result2 = biocacheService.getSpeciesListQuery(list("list1"), null);

            assertEquals(result1, result2);
            assertEquals(Map.of("query", "(lsid:lsid:1)"), result1);
            // The item scan runs only once: the second call is served from the cache
            verify(speciesListItemMongoRepository, times(1)).findNextBatch(eq("list1"), eq(null), any(Pageable.class));
        }

        @Test
        void sameFiltersInDifferentOrder_shareOneCacheEntry() throws Exception {
            ReflectionTestUtils.setField(biocacheService, "scopedQidCacheTtlSeconds", 600L);
            ReflectionTestUtils.setField(biocacheService, "maxRawQueryLength", 1);
            List<Filter> filtersA = List.of(
                    new Filter("classification.kingdom", "Animalia"),
                    new Filter("classification.family", "Fabaceae"));
            List<Filter> filtersB = List.of(
                    new Filter("classification.family", "Fabaceae"),
                    new Filter("classification.kingdom", "Animalia"));

            when(searchHelperService.searchSingleSpeciesList(any(), any(Pageable.class)))
                    .thenReturn(pageOf(items(1), 0, 200));
            doReturn("qid-list1").when(biocacheService).getQidForItems(anyList(), anyString(), anyLong());

            Map<String, String> resultA = biocacheService.getSpeciesListQuery(list("list1"), filtersA);
            Map<String, String> resultB = biocacheService.getSpeciesListQuery(list("list1"), filtersB);

            assertEquals(resultA, resultB);
            verify(searchHelperService, times(1)).searchSingleSpeciesList(any(), any(Pageable.class));
        }
    }
}
