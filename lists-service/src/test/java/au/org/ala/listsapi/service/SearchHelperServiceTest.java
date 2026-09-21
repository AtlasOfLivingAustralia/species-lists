package au.org.ala.listsapi.service;

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
}
