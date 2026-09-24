package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class SearchHelperServiceTest {

  @Mock private MongoTemplate mongoTemplate;
  
  @Mock private ElasticsearchOperations elasticsearchOperations;

  @InjectMocks private SearchHelperService searchHelperService;

  @Captor private ArgumentCaptor<Query> queryCaptor;

  @BeforeEach
  void setUp() {
    // Setup default mocks if needed
  }

  @Test
  void searchDocuments_withListType_addsListTypeToQuery() {
    // Arrange
    SpeciesList speciesListQuery = new SpeciesList();
    speciesListQuery.setListType("PROFILE");

    String userId = "user123";
    Boolean isAdmin = false;
    String searchTerm = "";
    Pageable pageable = PageRequest.of(0, 10);

    when(mongoTemplate.find(any(Query.class), eq(SpeciesList.class)))
        .thenReturn(Collections.emptyList());
    when(mongoTemplate.count(any(Query.class), eq(SpeciesList.class))).thenReturn(0L);

    // Act
    searchHelperService.searchDocuments(speciesListQuery, userId, isAdmin, searchTerm, pageable);

    // Assert
    verify(mongoTemplate).find(queryCaptor.capture(), eq(SpeciesList.class));
    Query capturedQuery = queryCaptor.getValue();

    String queryStr = capturedQuery.getQueryObject().toJson();
    assertTrue(
        queryStr.contains("\"listType\": \"PROFILE\""), "Query should contain listType filter");
  }

  @Test
  void getFacetsForSingleSpeciesList_ignoresEmptyFacetFields() {
    SingleListSearchContext context = SingleListSearchContext.builder().speciesListId("testListId").filters(Collections.emptyList()).build();
    
    // Mock elasticsearchOperations to return an empty SearchHits to avoid NPE when parsing aggregations
    org.springframework.data.elasticsearch.core.SearchHits<au.org.ala.listsapi.model.SpeciesListIndex> mockHits = org.mockito.Mockito.mock(org.springframework.data.elasticsearch.core.SearchHits.class);
    when(elasticsearchOperations.search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(au.org.ala.listsapi.model.SpeciesListIndex.class))).thenReturn(mockHits);

    // Act with a list containing an empty string
    searchHelperService.getFacetsForSingleSpeciesList(context, Arrays.asList("validField", "", null, "   "));
    // As long as it doesn't throw an Invalid aggregation name exception, we're good.
  }

  @Test
  void getFacetsForSpeciesLists_withFilter_appliesDisjunctiveFiltering() {
    Filter listTypeFilter = new Filter("listType", "CONSERVATION_LIST");
    ListSearchContext context = ListSearchContext.builder()
        .searchQuery("birds")
        .filters(List.of(listTypeFilter))
        .isAdmin(true)
        .build();

    SearchHits<SpeciesListIndex> mockHits = org.mockito.Mockito.mock(SearchHits.class);
    ArgumentCaptor<NativeQuery> nativeQueryCaptor = ArgumentCaptor.forClass(NativeQuery.class);

    when(elasticsearchOperations.search(nativeQueryCaptor.capture(), eq(SpeciesListIndex.class)))
        .thenReturn(mockHits);

    searchHelperService.getFacetsForSpeciesLists(context);

    NativeQuery captured = nativeQueryCaptor.getValue();
    assertNotNull(captured);

    // The listType aggregation should NOT be wrapped in a filter (otherFilters is empty for listType)
    co.elastic.clients.elasticsearch._types.aggregations.Aggregation listTypeAgg = 
        captured.getAggregations().get("listType");
    assertNotNull(listTypeAgg);
    assertTrue(listTypeAgg.isTerms(), "listType aggregation should be a direct terms aggregation");

    // The licence aggregation SHOULD be wrapped in a filter (otherFilters contains listType)
    co.elastic.clients.elasticsearch._types.aggregations.Aggregation licenceAgg = 
        captured.getAggregations().get("licence");
    assertNotNull(licenceAgg);
    assertTrue(licenceAgg.isFilter(), "licence aggregation should be wrapped in a filter aggregation");
  }

  @Test
  void getFacetsForSingleSpeciesList_withFilter_appliesDisjunctiveFiltering() {
    Filter familyFilter = new Filter("classification.family", "Fabaceae");
    SingleListSearchContext context = SingleListSearchContext.builder()
        .speciesListId("list-123")
        .searchQuery("Acacia")
        .filters(List.of(familyFilter))
        .build();

    SearchHits<SpeciesListIndex> mockHits = org.mockito.Mockito.mock(SearchHits.class);
    ArgumentCaptor<NativeQuery> nativeQueryCaptor = ArgumentCaptor.forClass(NativeQuery.class);

    when(elasticsearchOperations.search(nativeQueryCaptor.capture(), eq(SpeciesListIndex.class)))
        .thenReturn(mockHits);

    searchHelperService.getFacetsForSingleSpeciesList(context, List.of("status"));

    NativeQuery captured = nativeQueryCaptor.getValue();
    assertNotNull(captured);

    // The classification.family aggregation should NOT be wrapped in a filter (otherFilters is empty for family)
    co.elastic.clients.elasticsearch._types.aggregations.Aggregation familyAgg = 
        captured.getAggregations().get("classification.family");
    assertNotNull(familyAgg);
    assertTrue(familyAgg.isTerms(), "classification.family should be a direct terms aggregation");

    // The status aggregation SHOULD be wrapped in a filter (otherFilters contains family)
    co.elastic.clients.elasticsearch._types.aggregations.Aggregation statusAgg = 
        captured.getAggregations().get("status");
    assertNotNull(statusAgg);
    assertTrue(statusAgg.isFilter(), "status aggregation should be wrapped in a filter aggregation");
  }

  @Test
  void classificationFields_preservesProposedOrder() {
    List<String> expectedOrder = List.of(
        "classification.matchType",
        "classification.rank",
        "classification.kingdom",
        "classification.phylum",
        "classification.classs",
        "classification.order",
        "classification.family",
        "classification.genus",
        "classification.vernacularName",
        "classification.speciesSubgroup"
    );
    assertEquals(expectedOrder, SearchHelperService.CLASSIFICATION_FIELDS);
  }

  @Test
  void getFacetsForSingleSpeciesList_includesClassificationClasssAggregation() {
    SingleListSearchContext context = SingleListSearchContext.builder()
        .speciesListId("list-123")
        .filters(Collections.emptyList())
        .build();

    SearchHits<SpeciesListIndex> mockHits = org.mockito.Mockito.mock(SearchHits.class);
    ArgumentCaptor<NativeQuery> nativeQueryCaptor = ArgumentCaptor.forClass(NativeQuery.class);

    when(elasticsearchOperations.search(nativeQueryCaptor.capture(), eq(SpeciesListIndex.class)))
        .thenReturn(mockHits);

    searchHelperService.getFacetsForSingleSpeciesList(context, List.of("status"));

    NativeQuery captured = nativeQueryCaptor.getValue();
    assertNotNull(captured);

    co.elastic.clients.elasticsearch._types.aggregations.Aggregation classsAgg = 
        captured.getAggregations().get("classification.classs");
    assertNotNull(classsAgg, "Aggregation classification.classs must be present");
  }

  @Test
  void getFacetsForSingleSpeciesList_resolvesFieldListFromMongoWhenFacetFieldsEmpty() {
    SpeciesList speciesList = new SpeciesList();
    speciesList.setId("list-fieldlist-test");
    speciesList.setFieldList(List.of("status", "location", "notes"));

    SingleListSearchContext context = SingleListSearchContext.builder()
        .speciesListId("list-fieldlist-test")
        .speciesList(speciesList)
        .filters(Collections.emptyList())
        .build();

    SearchHits<SpeciesListIndex> mockHits = org.mockito.Mockito.mock(SearchHits.class);
    ArgumentCaptor<NativeQuery> nativeQueryCaptor = ArgumentCaptor.forClass(NativeQuery.class);

    when(elasticsearchOperations.search(nativeQueryCaptor.capture(), eq(SpeciesListIndex.class)))
        .thenReturn(mockHits);

    // Call with empty facetFields (as sent by UI)
    searchHelperService.getFacetsForSingleSpeciesList(context, Collections.emptyList());

    NativeQuery captured = nativeQueryCaptor.getValue();
    assertNotNull(captured);

    // status, location, notes aggregations should be registered from fieldList
    assertNotNull(captured.getAggregations().get("status"), "Aggregation for field status from fieldList should be present");
    assertNotNull(captured.getAggregations().get("location"), "Aggregation for field location from fieldList should be present");
    assertNotNull(captured.getAggregations().get("notes"), "Aggregation for field notes from fieldList should be present");
  }
}
