package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import au.org.ala.listsapi.model.SpeciesList;
import java.util.Collections;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class SearchHelperServiceTest {

  @Mock private MongoTemplate mongoTemplate;

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
}
