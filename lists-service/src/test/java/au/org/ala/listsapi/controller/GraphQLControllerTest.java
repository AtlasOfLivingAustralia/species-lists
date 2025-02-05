package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.*;
import au.org.ala.listsapi.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.security.Principal;
import java.util.*;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GraphQLControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    private GraphQlTester graphQlTester;

    @MockBean
    private SpeciesListMongoRepository speciesListMongoRepository;

    @MockBean
    private SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @MockBean
    private ReleaseMongoRepository releaseMongoRepository;

    @MockBean
    private SpeciesListIndexElasticRepository speciesListIndexElasticRepository;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private TaxonService taxonService;

    @MockBean
    private ValidationService validationService;

    @MockBean
    private AuthUtils authUtils;

    @MockBean
    private MetadataService metadataService;

    @SpyBean
    private GraphQLController graphQLController;

    @BeforeEach
    void setupMocks() {
        // By default, assume user is authorized
        Mockito.when(authUtils.isAuthorized(any(Principal.class))).thenReturn(true);
        Mockito.when(authUtils.isAuthorized(any(SpeciesList.class), any(Principal.class))).thenReturn(true);

        // By default, assume all validations pass
        Mockito.when(validationService.isValueValid(any(), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("listReleases query - returns releases for a given list")
    void testListReleasesQuery() {
        // Mock data
        Release release1 = new Release();
        release1.setId("release-1");
        release1.setSpeciesListID("test-list-id");
        release1.setReleasedVersion(1);

        given(releaseMongoRepository.findBySpeciesListID(eq("test-list-id"), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(release1)));

        String query = """
            query($speciesListID:String,$page:Int,$size:Int){
              listReleases(speciesListID:$speciesListID, page:$page, size:$size){
                  id
                  speciesListID
                  releasedVersion
              }
            }
            """;

        graphQlTester.document(query)
                .variable("speciesListID", "test-list-id")
                .variable("page", 0)
                .variable("size", 5)
                .execute()
                .path("listReleases").entityList(Release.class).hasSize(1)
                .path("listReleases[0].id").entity(String.class).isEqualTo("release-1")
                .path("listReleases[0].speciesListID").entity(String.class).isEqualTo("test-list-id")
                .path("listReleases[0].releasedVersion").entity(Integer.class).isEqualTo(1);
    }

    @Test
    @DisplayName("getSpeciesListMetadata query - returns single list metadata")
    void testGetSpeciesListMetadata() {
        SpeciesList list = new SpeciesList();
        list.setId("test-list-id");
        list.setIsPrivate(false);

        given(speciesListMongoRepository.findByIdOrDataResourceUid(eq("test-list-id"), eq("test-list-id")))
                .willReturn(Optional.of(list));

        String query = """
            query($listId: String!){
              getSpeciesListMetadata(speciesListID: $listId) {
                id
                isPrivate
              }
            }
            """;

        graphQlTester.document(query)
                .variable("listId", "test-list-id")
                .execute()
                .path("getSpeciesListMetadata.id").entity(String.class).isEqualTo("test-list-id")
                .path("getSpeciesListMetadata.isPrivate").entity(Boolean.class).isEqualTo(false);
    }

    @Nested
    @DisplayName("Mutations")
    class MutationsTests {

        @Test
        @DisplayName("addField mutation - adds a field to an existing species list")
        void testAddField() {
            // Mock existing species list
            SpeciesList list = new SpeciesList();
            list.setId("test-list-id");
            list.setFieldList(new ArrayList<>());
            list.setIsPrivate(false);

            // Return that list from the repository
            given(speciesListMongoRepository.findByIdOrDataResourceUid("test-list-id","test-list-id"))
                    .willReturn(Optional.of(list));

            // Mock speciesListItemMongoRepository call so it doesn't return null
            given(speciesListItemMongoRepository.findBySpeciesListIDOrderById(eq("test-list-id"), any()))
                    .willReturn(Page.empty());

            given(speciesListMongoRepository.save(any(SpeciesList.class))).willReturn(list);

            // Define the mutation
            String mutation = """
                mutation($id:String!, $fieldName:String!, $fieldValue:String!) {
                  addField(id:$id, fieldName:$fieldName, fieldValue:$fieldValue){
                    id
                    fieldList
                  }
                }
                """;

            // Execute the mutation
            graphQlTester.document(mutation)
                    .variable("id", "test-list-id")
                    .variable("fieldName", "extraField")
                    .variable("fieldValue", "someValue")
                    .execute()
                    .path("addField.id").entity(String.class).isEqualTo("test-list-id")
                    .path("addField.fieldList").entityList(String.class).contains("extraField");

            // Verify list was saved
            verify(speciesListMongoRepository).save(Mockito.any(SpeciesList.class));
        }

        @Test
        @DisplayName("removeSpeciesListItem mutation - removes an item from the list")
        void testRemoveSpeciesListItem() {
            SpeciesListItem item = new SpeciesListItem();
            item.setId("item-1");
            item.setSpeciesListID("test-list-id");
            given(speciesListItemMongoRepository.findById(eq("item-1"))).willReturn(Optional.of(item));

            SpeciesList list = new SpeciesList();
            list.setId("test-list-id");
            given(speciesListMongoRepository.findById(eq("test-list-id"))).willReturn(Optional.of(list));

            String mutation = """
                mutation($id:String!){
                  removeSpeciesListItem(id:$id){
                    id
                    speciesListID
                  }
                }
                """;

            graphQlTester.document(mutation)
                    .variable("id", "item-1")
                    .execute()
                    .path("removeSpeciesListItem.id").entity(String.class).isEqualTo("item-1")
                    .path("removeSpeciesListItem.speciesListID").entity(String.class).isEqualTo("test-list-id");

            // Verify repository calls
            verify(speciesListItemMongoRepository).deleteById("item-1");
            verify(speciesListIndexElasticRepository).deleteById("item-1");
        }

        @Test
        @DisplayName("updateMetadata mutation - updates species list metadata")
        void testUpdateMetadata() throws Exception {
            SpeciesList list = new SpeciesList();
            list.setId("test-list-id");
            list.setTitle("Old Title");
            list.setListType("TEST");
            list.setLicence("CC0");
            list.setIsPrivate(true);
            list.setIsAuthoritative(false);

            given(speciesListMongoRepository.findByIdOrDataResourceUid(eq("test-list-id"), eq("test-list-id")))
                    .willReturn(Optional.of(list));


            given(speciesListMongoRepository.save(any(SpeciesList.class))).willReturn(list);

            String mutation = """
                mutation($id:String!, $title:String!, $licence:String!, $listType:String!, $isPrivate:Boolean!, $isAuthoritative:Boolean!){
                  updateMetadata(id:$id, title:$title, licence:$licence, listType:$listType, isPrivate:$isPrivate, isAuthoritative:$isAuthoritative){
                    id
                    title
                    licence
                    listType
                    isPrivate
                    isAuthoritative
                  }
                }
                """;

            graphQlTester.document(mutation)
                    .variable("id", "test-list-id")
                    .variable("title", "Updated Title")
                    .variable("licence", "CC-BY")
                    .variable("listType", "LOCAL_LIST")
                    .variable("isPrivate", true)
                    .variable("isAuthoritative", false)
                    .execute()
                    .path("updateMetadata.id").entity(String.class).isEqualTo("test-list-id")
                    .path("updateMetadata.title").entity(String.class).isEqualTo("Updated Title")
                    .path("updateMetadata.licence").entity(String.class).isEqualTo("CC-BY")
                    .path("updateMetadata.listType").entity(String.class).isEqualTo("LOCAL_LIST")
                    .path("updateMetadata.isPrivate").entity(Boolean.class).isEqualTo(true)
                    .path("updateMetadata.isAuthoritative").entity(Boolean.class).isEqualTo(false);

            verify(speciesListMongoRepository).save(Mockito.any(SpeciesList.class));
        }
    }

    @Test
    @DisplayName("filterSpeciesList query - applies search + filter on a single list")
    void testFilterSpeciesList() {
        // 1) Mock a non-null 'SpeciesList' so that repository lookup works.
        SpeciesList list = new SpeciesList();
        list.setId("test-list-id");
        list.setIsPrivate(false);
        given(speciesListMongoRepository.findByIdOrDataResourceUid("test-list-id","test-list-id"))
                .willReturn(Optional.of(list));

        // 2) Mock ElasticsearchOperations to return empty SearchHits rather than null.
        //    That ensures convertList(...) doesn't see a null list.
        SearchHits<SpeciesListIndex> mockHits = Mockito.mock(SearchHits.class);
        Mockito.when(mockHits.getSearchHits()).thenReturn(Collections.emptyList()); // an empty list
        Mockito.when(mockHits.getTotalHits()).thenReturn(0L);

        given(elasticsearchOperations.search(
                any(Query.class),
                eq(SpeciesListIndex.class),
                any(IndexCoordinates.class))
        ).willReturn(mockHits);

        // 3) Now run your GraphQL query
        String query = """
        query($speciesListID: String!, $page:Int, $size:Int){
          filterSpeciesList(speciesListID:$speciesListID, page:$page, size:$size){
            content {
              id
              scientificName
            }
            totalElements
          }
        }
        """;

        graphQlTester.document(query)
                .variable("speciesListID", "test-list-id")
                .variable("page", 0)
                .variable("size", 5)
                .execute()
                .path("filterSpeciesList.content").entityList(SpeciesListItem.class).hasSize(0)
                .path("filterSpeciesList.totalElements").entity(Long.class).isEqualTo(0L);
    }

    @Test
    @DisplayName("getTaxonImage query - returns a single taxon image if found")
    void testGetTaxonImage() throws Exception {
        // 1) Mock what loadJson(...) should return
        Map<String, Object> bieResponse = new HashMap<>();
        bieResponse.put("imageIdentifier", "image-123");
        // The getTaxonImage method will form a URL like "https://bie-ws.ala.org.au/ws/species/test-taxon-id"
        // and then call loadJson(...) with that URL. We can match any string or be exact:
        Mockito.doReturn(bieResponse).when(graphQLController).loadJson(Mockito.contains("/test-taxon-id"));

        // 3) Define your GraphQL query
        String query = """
        query($taxonID:String!) {
          getTaxonImage(taxonID: $taxonID) {
            url
          }
        }
        """;

        // 4) Execute the query with GraphQlTester
        graphQlTester.document(query)
                .variable("taxonID", "test-taxon-id")
                .execute()
                // 5) Expect a non-null Image with the mocked URL
                .path("getTaxonImage.url")
                .entity(String.class)
                .satisfies(url -> {
                    // e.g. verify it includes "image-123" from the BIE response
                    // (the placeholder replacement in your code will produce https://some-domain.org/images/image-123.jpg)
                    org.assertj.core.api.Assertions.assertThat(url)
                            .contains("image-123");
                });
    }

    @Test
    @DisplayName("GraphQL exception handler - returns ValidationError when exception is thrown")
    void testGraphQLExceptionHandler() {
        // Force an exception inside the controller by forbidding access
        Mockito.when(authUtils.isAuthorized(any(Principal.class))).thenReturn(false);

        String query = """
            query {
              lists(searchQuery:"abc", page:0, size:10, userId:null, isPrivate:true){
                totalElements
              }
            }
            """;

        graphQlTester.document(query)
                .execute()
                .errors()
                .expect(err ->
                        err.getErrorType() == graphql.ErrorType.ValidationError
                                && err.getMessage().contains("You dont have access to this list"))
                .verify();
    }
}