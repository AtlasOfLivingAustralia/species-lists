package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.InputSpeciesListItem;
import au.org.ala.listsapi.service.SearchHelperService;
import au.org.ala.listsapi.service.TaxonService;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.ws.security.profile.AlaUserProfile;

@ExtendWith(MockitoExtension.class)
class GraphQLControllerTest {

    @Mock
    private SearchHelperService searchHelperService;

    @Mock
    private SpeciesListMongoRepository speciesListMongoRepository;

    @Mock
    private SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @Mock
    private TaxonService taxonService;

    @Mock
    private AuthUtils authUtils;

    @Mock
    private SpeciesListIndexElasticRepository speciesListIndexElasticRepository;

    @Mock
    private Principal principal;

    @InjectMocks
    private GraphQLController graphQLController;

    private AlaUserProfile userProfile;
    private AlaUserProfile adminProfile;

    @BeforeEach
    void setUp() {
        userProfile = mock(AlaUserProfile.class);
        lenient().when(userProfile.getUserId()).thenReturn("user123");

        adminProfile = mock(AlaUserProfile.class);
        lenient().when(adminProfile.getUserId()).thenReturn("admin123");
    }

    @Test
    void testLists_PublicSearch_NoAuth() {
        List<SpeciesList> mockLists = new ArrayList<>();
        Page<SpeciesList> mockPage = new PageImpl<>(mockLists);

        when(authUtils.getUserProfile(principal)).thenReturn(null);
        when(searchHelperService.searchSpeciesLists(any(ListSearchContext.class), any(PageRequest.class)))
                .thenReturn(mockPage);

        Page<SpeciesList> result = graphQLController.lists(
                "kangaroo", null, 0, 10, null, "relevance", false, "desc", principal);

        assertNotNull(result);
        verify(searchHelperService).searchSpeciesLists(any(ListSearchContext.class), any(PageRequest.class));
    }

    @Test
    void testLists_PrivateSearch_NoAuth_ThrowsException() {
        when(authUtils.getUserProfile(principal)).thenReturn(null);

        // Even without explicitly passing isPrivate=true, if the filters say isPrivate=true it will throw
        List<Filter> filters = new ArrayList<>();
        filters.add(new Filter("isPrivate", "true"));

        assertThrows(AccessDeniedException.class, () -> {
            graphQLController.lists("kangaroo", filters, 0, 10, null, "relevance", true, "desc", principal);
        });
    }

    @Test
    void testLists_UserViewsOwnPrivateLists() {
        when(authUtils.getUserProfile(principal)).thenReturn(userProfile);
        when(authUtils.hasAdminRole(userProfile)).thenReturn(false);
        when(authUtils.hasInternalScope(userProfile)).thenReturn(false);

        List<SpeciesList> mockLists = new ArrayList<>();
        Page<SpeciesList> mockPage = new PageImpl<>(mockLists);
        
        when(searchHelperService.searchSpeciesLists(any(ListSearchContext.class), any(PageRequest.class)))
                .thenReturn(mockPage);

        Page<SpeciesList> result = graphQLController.lists(
                "", null, 0, 10, "user123", "relevance", true, "desc", principal);

        assertNotNull(result);
        verify(searchHelperService).searchSpeciesLists(argThat(context -> 
            "user123".equals(context.getUserId()) && context.getFilters().stream().anyMatch(f -> "isPrivate".equals(f.getKey()) && "true".equals(f.getValue()))
        ), any(PageRequest.class));
    }

    @Test
    void testLists_UserViewsOtherUserLists_ThrowsException() {
        when(authUtils.getUserProfile(principal)).thenReturn(userProfile);
        when(authUtils.hasAdminRole(userProfile)).thenReturn(false);
        when(authUtils.hasInternalScope(userProfile)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> {
            graphQLController.lists("", null, 0, 10, "otherUser456", "relevance", false, "desc", principal);
        });
    }

    @Test
    void testFacetSpeciesLists() {
        when(authUtils.getUserProfile(principal)).thenReturn(userProfile);
        when(authUtils.hasAdminRole(userProfile)).thenReturn(false);
        when(authUtils.hasInternalScope(userProfile)).thenReturn(false);

        List<Facet> mockFacets = new ArrayList<>();
        when(searchHelperService.getFacetsForSpeciesLists(any(ListSearchContext.class)))
                .thenReturn(mockFacets);

        List<Facet> result = graphQLController.facetSpeciesLists(
                "birds", null, null, false, 0, 10, principal);

        assertNotNull(result);
        verify(searchHelperService).getFacetsForSpeciesLists(any(ListSearchContext.class));
    }

    @Test
    void testAddSpeciesListItem_HexId() {
        InputSpeciesListItem input = new InputSpeciesListItem();
        input.setSpeciesListID("60b9b3b3e6b3a32b00000000");
        input.setScientificName("Macropus giganteus");
        input.setProperties(new ArrayList<>());

        SpeciesList list = new SpeciesList();
        list.setId("60b9b3b3e6b3a32b00000000");
        list.setRowCount(0);

        when(speciesListMongoRepository.findByIdOrDataResourceUid("60b9b3b3e6b3a32b00000000", "60b9b3b3e6b3a32b00000000"))
                .thenReturn(Optional.of(list));
        when(speciesListMongoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(speciesListItemMongoRepository.save(any())).thenAnswer(i -> {
            SpeciesListItem item = i.getArgument(0);
            item.setId(new ObjectId());
            return item;
        });
        when(principal.getName()).thenReturn("user123");

        SpeciesListItem result = graphQLController.addSpeciesListItem(input, principal);

        assertNotNull(result);
        assertEquals("60b9b3b3e6b3a32b00000000", result.getSpeciesListID());
        assertEquals("Macropus giganteus", result.getScientificName());
        verify(speciesListMongoRepository).findByIdOrDataResourceUid("60b9b3b3e6b3a32b00000000", "60b9b3b3e6b3a32b00000000");
        verify(speciesListItemMongoRepository).save(any());
        verify(taxonService).lookupTaxon(any());
    }

    @Test
    void testAddSpeciesListItem_DR1234Id() {
        InputSpeciesListItem input = new InputSpeciesListItem();
        input.setSpeciesListID("dr1234");
        input.setScientificName("Macropus rufus");
        input.setProperties(new ArrayList<>());

        SpeciesList list = new SpeciesList();
        list.setId("60b9b3b3e6b3a32b00000001");
        list.setDataResourceUid("dr1234");
        list.setRowCount(0);

        when(speciesListMongoRepository.findByIdOrDataResourceUid("dr1234", "dr1234"))
                .thenReturn(Optional.of(list));
        when(speciesListMongoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(speciesListItemMongoRepository.save(any())).thenAnswer(i -> {
            SpeciesListItem item = i.getArgument(0);
            item.setId(new ObjectId());
            return item;
        });
        when(principal.getName()).thenReturn("user123");

        SpeciesListItem result = graphQLController.addSpeciesListItem(input, principal);

        assertNotNull(result);
        assertEquals("60b9b3b3e6b3a32b00000001", result.getSpeciesListID(), "Internal hex ID should override the dr1234 ID");
        assertEquals("Macropus rufus", result.getScientificName());
        verify(speciesListMongoRepository).findByIdOrDataResourceUid("dr1234", "dr1234");
        verify(speciesListItemMongoRepository).save(any());
        verify(taxonService).lookupTaxon(any());
    }
}
