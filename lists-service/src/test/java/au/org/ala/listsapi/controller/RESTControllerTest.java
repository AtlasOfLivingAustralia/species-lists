package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.*;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.BiocacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.*;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RESTControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpeciesListMongoRepository speciesListMongoRepository;

    @MockBean
    private SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @MockBean
    private BiocacheService biocacheService;

    @MockBean
    private AuthUtils authUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        // By default, let's assume the user is authorized if they have any principal at all.
        when(authUtils.isAuthorized(any(Principal.class))).thenReturn(true);
        when(authUtils.isAuthorized(any(SpeciesList.class), any(Principal.class))).thenReturn(true);
    }

    @Test
    @DisplayName("GET /info should return app name & version from @Value properties")
    void testInfo() throws Exception {
        mockMvc.perform(get("/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    @DisplayName("GET /speciesList/{id} should return 200 with the SpeciesList if found, else 404")
    void testGetSpeciesListByID() throws Exception {
        SpeciesList list = new SpeciesList();
        list.setId("test-id");
        list.setTitle("My Test List");
        list.setIsPrivate(false);

        given(speciesListMongoRepository.findByIdOrDataResourceUid("test-id","test-id"))
                .willReturn(Optional.of(list));

        // Found scenario
        mockMvc.perform(get("/speciesList/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.title").value("My Test List"))
                .andExpect(jsonPath("$.isPrivate").value(false));

        // Not found scenario
        given(speciesListMongoRepository.findByIdOrDataResourceUid("not-found","not-found"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/speciesList/not-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /speciesList/ should return a paged list of species lists in legacy format")
    void testSpeciesListsQuery() throws Exception {
        // Suppose we have 2 lists in total
        SpeciesList list1 = new SpeciesList();
        list1.setId("list-1");
        list1.setTitle("List One");
        list1.setIsPrivate(false);

        SpeciesList list2 = new SpeciesList();
        list2.setId("list-2");
        list2.setTitle("List Two");
        list2.setIsPrivate(false);

        Page<SpeciesList> pageResult = new PageImpl<>(List.of(list1, list2), PageRequest.of(0, 10), 2);
        given(speciesListMongoRepository.findAll(any(Pageable.class))).willReturn(pageResult);

        // We also mock that user is authorized:
        when(authUtils.isAuthorized(any())).thenReturn(true);

        mockMvc.perform(get("/speciesList/")
                        .param("page", "1")       // the controller subtracts 1, so real page=0
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                // The controller wraps the result in a "legacy" format with "listCount", "offset", "max", "lists" keys
                .andExpect(jsonPath("$.listCount").value(2))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.max").value(10))
                .andExpect(jsonPath("$.lists[0].id").value("list-1"))
                .andExpect(jsonPath("$.lists[1].id").value("list-2"));
    }

    @Test
    @DisplayName("GET /speciesList/ with search fields => calls findAll(example, paging)")
    void testSpeciesListsQueryWithExample() throws Exception {
        // Suppose the user is not authorized => isPrivate forced to "false"
        when(authUtils.isAuthorized(any(Principal.class))).thenReturn(false);

        // Provide a single list in the results
        SpeciesList onlyList = new SpeciesList();
        onlyList.setId("list-1");
        onlyList.setTitle("Public List");
        onlyList.setIsPrivate(false);

        Page<SpeciesList> pageResult = new PageImpl<>(List.of(onlyList), PageRequest.of(0, 5), 1);
        given(speciesListMongoRepository.findAll(any(Example.class), any(Pageable.class))).willReturn(pageResult);

        mockMvc.perform(get("/speciesList/")
                        .param("page", "1")
                        .param("pageSize", "5")
                        // pass some query param that would fill RESTSpeciesListQuery
                        .param("title", "something")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listCount").value(1))
                .andExpect(jsonPath("$.lists[0].id").value("list-1"));
    }

    @Test
    @DisplayName("GET /speciesListItems/{id} => returns items if user authorized, or 400 if private & user not authorized, or 404 if list absent")
    void testSpeciesListItems() throws Exception {
        // Mock the species list
        SpeciesList list = new SpeciesList();
        list.setId("test-list-id");
        list.setTitle("My Private List");
        list.setIsPrivate(true);

        given(speciesListMongoRepository.findByIdOrDataResourceUid("test-list-id","test-list-id"))
                .willReturn(Optional.of(list));

        // If user not authorized -> 400
        when(authUtils.isAuthorized(eq(list), any())).thenReturn(false);

        mockMvc.perform(get("/speciesListItems/test-list-id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("You don't have access to this list"));

        // If user is authorized -> return list items
        when(authUtils.isAuthorized(eq(list), any())).thenReturn(true);

        SpeciesListItem item1 = new SpeciesListItem();
        item1.setId("item-1");
        item1.setScientificName("Species A");

        SpeciesListItem item2 = new SpeciesListItem();
        item2.setId("item-2");
        item2.setScientificName("Species B");

        Page<SpeciesListItem> itemsPage =
                new PageImpl<>(List.of(item1, item2), PageRequest.of(0, 10), 2);

        given(speciesListItemMongoRepository.findBySpeciesListIDOrderById("test-list-id", PageRequest.of(0, 10)))
                .willReturn(itemsPage);

        mockMvc.perform(get("/speciesListItems/test-list-id")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("item-1"))
                .andExpect(jsonPath("$[1].id").value("item-2"));

        // If list is absent -> 404
        given(speciesListMongoRepository.findByIdOrDataResourceUid("missing","missing"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/speciesListItems/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Species list not found"));
    }

    @Test
    @DisplayName("GET /speciesListQid/{id} => returns qid from biocacheService or 404 if list not found")
    void testSpeciesListQid() throws Exception {
        SpeciesList list = new SpeciesList();
        list.setId("test-list-id");
        list.setTitle("Some List");
        list.setIsPrivate(false);

        given(speciesListMongoRepository.findByIdOrDataResourceUid("test-list-id","test-list-id"))
                .willReturn(Optional.of(list));

        // Suppose the biocache service returns some QID
        given(biocacheService.getQidForSpeciesList("test-list-id")).willReturn("QID-123");

        mockMvc.perform(get("/speciesListQid/test-list-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qid").value("QID-123"));

        // 404 if not found
        given(speciesListMongoRepository.findByIdOrDataResourceUid("missing","missing"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/speciesListQid/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Species list not found"));
    }
}