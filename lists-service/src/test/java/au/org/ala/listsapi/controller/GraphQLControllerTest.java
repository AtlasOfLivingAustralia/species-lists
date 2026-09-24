package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.types.ObjectId;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.InputSpeciesListItem;
import au.org.ala.listsapi.service.MetadataService;
import au.org.ala.listsapi.service.SearchHelperService;
import au.org.ala.listsapi.service.TaxonService;
import au.org.ala.listsapi.service.ValidationService;
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
    private ValidationService validationService;

    @Mock
    private MetadataService metadataService;

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
    void testFacetSpeciesList_usesFieldListWhenFacetFieldsEmpty() {
        String listId = "list-single-test";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setIsPrivate(false);
        list.setFieldList(List.of("family", "vernacularName", "status", "sourceStatus", "WildNetTaxonID", "IUCN_equivalent_status"));
        list.setFacetList(List.of("status"));

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(searchHelperService.getFacetsForSingleSpeciesList(any(SingleListSearchContext.class), fieldsCaptor.capture()))
                .thenReturn(Collections.emptyList());

        List<Facet> result = graphQLController.facetSpeciesList(
                listId, null, null, Collections.emptyList(), 0, 10, principal);

        assertNotNull(result);
        assertEquals(list.getFieldList(), fieldsCaptor.getValue());
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
        verify(speciesListItemMongoRepository, times(2)).save(any());
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
        verify(speciesListItemMongoRepository, times(2)).save(any());
        verify(taxonService).lookupTaxon(any());
    }

    @Test
    void testAddField_ReindexesList() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setFieldList(new ArrayList<>());
        
        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));
        
        // Mock the pagination for finding items
        when(speciesListItemMongoRepository.findNextBatch(eq(listId), isNull(), any(PageRequest.class)))
                .thenReturn(new ArrayList<>());

        SpeciesList result = graphQLController.addField(listId, "newField", "defaultValue", principal);

        assertNotNull(result);
        assertTrue(result.getFieldList().contains("newField"));
        verify(speciesListMongoRepository).save(list);
        verify(taxonService).reindex(listId);
    }

    @Test
    void testRenameField_ReindexesList() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        List<String> fields = new ArrayList<>();
        fields.add("oldField");
        list.setFieldList(fields);
        
        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));
        
        when(speciesListItemMongoRepository.findNextBatch(eq(listId), isNull(), any(PageRequest.class)))
                .thenReturn(new ArrayList<>());

        SpeciesList result = graphQLController.renameField(listId, "oldField", "newField", principal);

        assertNotNull(result);
        assertFalse(result.getFieldList().contains("oldField"));
        assertTrue(result.getFieldList().contains("newField"));
        verify(speciesListMongoRepository).save(list);
        verify(taxonService).reindex(listId);
    }

    @Test
    void testRemoveField_ReindexesList() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        List<String> fields = new ArrayList<>();
        fields.add("fieldToRemove");
        list.setFieldList(fields);
        
        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));
        
        when(speciesListItemMongoRepository.findNextBatch(eq(listId), isNull(), any(PageRequest.class)))
                .thenReturn(new ArrayList<>());

        SpeciesList result = graphQLController.removeField(listId, "fieldToRemove", principal);

        assertNotNull(result);
        assertFalse(result.getFieldList().contains("fieldToRemove"));
        verify(speciesListMongoRepository).save(list);
        verify(taxonService).reindex(listId);
    }

    @Test
    void testUpdateMetadata_BlankDataResourceUid_TreatedAsNoChangeWhenListHasNullUid() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setDataResourceUid(null);
        list.setTitle("Old Title");
        list.setIsPrivate(true);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), any())).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "New Title", "description", "CC-BY", "TEST",
                "authority", "region", null, true, false, false,
                false, false, false, false, new ArrayList<>(), "", principal);

        assertNotNull(result);
        assertEquals("New Title", result.getTitle());
        assertNull(result.getDataResourceUid());
        verify(speciesListMongoRepository, never()).findByDataResourceUid(any());
        verify(speciesListMongoRepository).save(list);
    }

    @Test
    void testUpdateMetadata_PrivateBiosecurityList_CallsSetMeta() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setDataResourceUid(null);
        list.setTitle("Biosecurity Alert List");
        list.setIsPrivate(true);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(authUtils.isAdmin(principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), any())).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "Biosecurity Alert List", "description", "CC-BY", "TEST",
                "authority", "region", null, true, false, false,
                false, false, false, true, new ArrayList<>(), "", principal);

        assertNotNull(result);
        assertTrue(result.getIsBiosecurity());
        assertTrue(result.getIsPrivate());
        verify(metadataService).setMeta(list);
    }

    @Test
    void testUpdateMetadata_NonAdminSettingBiosecurityFlag_ThrowsAccessDeniedException() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Test List");
        list.setIsBiosecurity(false);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(authUtils.isAdmin(principal)).thenReturn(false);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), any())).thenReturn(true);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            graphQLController.updateMetadata(
                    listId, "Test List", "description", "CC-BY", "TEST",
                    "authority", "region", null, true, false, false,
                    false, false, false, true, new ArrayList<>(), "", principal);
        });

        assertTrue(exception.getMessage().contains("isBiosecurity"));
        verify(speciesListMongoRepository, never()).save(any());
    }

    @Test
    void testUpdateMetadata_NonAdminSettingMultipleAdminFlags_ThrowsAccessDeniedException() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Test List");

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(authUtils.isAdmin(principal)).thenReturn(false);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), any())).thenReturn(true);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            graphQLController.updateMetadata(
                    listId, "Test List", "description", "CC-BY", "TEST",
                    "authority", "region", null, true, true, false,
                    false, false, false, true, new ArrayList<>(), "", principal);
        });

        assertTrue(exception.getMessage().contains("isThreatened"));
        assertTrue(exception.getMessage().contains("isBiosecurity"));
        verify(speciesListMongoRepository, never()).save(any());
    }

    @Test
    void testUpdateMetadata_AdminChangesDataResourceUid_Success() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setDataResourceUid(null);
        list.setTitle("Title");
        list.setIsPrivate(true);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(authUtils.isAdmin(principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), any())).thenReturn(true);
        when(speciesListMongoRepository.findByDataResourceUid("dr123")).thenReturn(Optional.empty());
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "Title", "description", "CC-BY", "TEST",
                "authority", "region", null, true, false, false,
                false, false, false, false, new ArrayList<>(), "dr123", principal);

        assertNotNull(result);
        assertEquals("dr123", result.getDataResourceUid());
        verify(speciesListMongoRepository).findByDataResourceUid("dr123");
        verify(speciesListMongoRepository).save(list);
    }

    @Test
    void testUpdateMetadata_DuplicateDataResourceUid_ThrowsException() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setDataResourceUid(null);
        list.setTitle("Title");

        SpeciesList otherList = new SpeciesList();
        otherList.setId("other456");
        otherList.setDataResourceUid("dr123");

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(authUtils.isAdmin(principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), any())).thenReturn(true);
        when(speciesListMongoRepository.findByDataResourceUid("dr123")).thenReturn(Optional.of(otherList));

        Exception exception = assertThrows(Exception.class, () -> {
            graphQLController.updateMetadata(
                    listId, "Title", "description", "CC-BY", "TEST",
                    "authority", "region", null, true, false, false,
                    false, false, false, false, new ArrayList<>(), "dr123", principal);
        });

        assertEquals("dataResourceUid is already in use by another list", exception.getMessage());
        verify(speciesListMongoRepository, never()).save(any());
    }

    @Test
    void testUpdateMetadata_NonAdminUnchangedAdminFlags_Success() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Old Title");
        list.setIsBiosecurity(true);
        list.setIsAuthoritative(false);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(authUtils.isAdmin(principal)).thenReturn(false);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), any())).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "New Title", "description", "CC-BY", "TEST",
                "authority", "region", null, false, false, false,
                false, false, false, true, new ArrayList<>(), "", principal);

        assertNotNull(result);
        assertEquals("New Title", result.getTitle());
        assertTrue(result.getIsBiosecurity());
        verify(speciesListMongoRepository).save(list);
    }

    @Test
    void testUpdateMetadata_PrivateList_LicenceOptional_Success() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Old Title");
        list.setIsPrivate(true);
        list.setLicence("CC-BY");

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "New Title", "description", "", "TEST",
                "authority", "region", null, true, false, false,
                false, false, false, false, new ArrayList<>(), "", principal);

        assertNotNull(result);
        assertNull(result.getLicence());
        assertTrue(result.getIsPrivate());
        verify(speciesListMongoRepository).save(list);
    }

    @Test
    void testUpdateMetadata_OmittedLicence_PreservesExistingLicenceOnPrivateList() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Old Title");
        list.setIsPrivate(true);
        list.setLicence("CC-BY");

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), eq("CC-BY"))).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "New Title", "description", null, "TEST",
                "authority", "region", null, true, false, false,
                false, false, false, false, new ArrayList<>(), "", principal);

        assertNotNull(result);
        assertEquals("CC-BY", result.getLicence());
        assertTrue(result.getIsPrivate());
        verify(speciesListMongoRepository).save(list);
    }

    @Test
    void testUpdateMetadata_OmittedLicence_PreservesExistingLicenceOnPublicList() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Public List");
        list.setIsPrivate(false);
        list.setLicence("CC-BY");

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), eq("CC-BY"))).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "Updated Public List", "new description", null, "TEST",
                "authority", "region", null, false, false, false,
                false, false, false, false, new ArrayList<>(), "", principal);

        assertNotNull(result);
        assertEquals("CC-BY", result.getLicence());
        assertFalse(result.getIsPrivate());
        verify(speciesListMongoRepository).save(list);
    }

    @Test
    void testUpdateMetadata_PublicList_NoLicence_ThrowsException() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Public List");
        list.setIsPrivate(false);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);

        Exception exception = assertThrows(Exception.class, () -> {
            graphQLController.updateMetadata(
                    listId, "Public List", "description", null, "TEST",
                    "authority", "region", null, false, false, false,
                    false, false, false, false, new ArrayList<>(), "", principal);
        });

        assertEquals("A valid licence is required for public lists", exception.getMessage());
        verify(speciesListMongoRepository, never()).save(any());
    }

    @Test
    void testUpdateMetadata_PrivateToPublic_NoLicence_ThrowsException() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Private List");
        list.setIsPrivate(true);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);

        Exception exception = assertThrows(Exception.class, () -> {
            graphQLController.updateMetadata(
                    listId, "Now Public List", "description", "", "TEST",
                    "authority", "region", null, false, false, false,
                    false, false, false, false, new ArrayList<>(), "", principal);
        });

        assertEquals("A valid licence is required for public lists", exception.getMessage());
        verify(speciesListMongoRepository, never()).save(any());
    }

    @Test
    void testUpdateMetadata_PrivateList_InvalidLicence_ThrowsException() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Private List");
        list.setIsPrivate(true);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.licence), eq("INVALID_LICENCE"))).thenReturn(false);

        Exception exception = assertThrows(Exception.class, () -> {
            graphQLController.updateMetadata(
                    listId, "Private List", "description", "INVALID_LICENCE", "TEST",
                    "authority", "region", null, true, false, false,
                    false, false, false, false, new ArrayList<>(), "", principal);
        });

        assertEquals("Updated list contains invalid properties for a controlled value (list type, license)", exception.getMessage());
        verify(speciesListMongoRepository, never()).save(any());
    }

    @Test
    void testUpdateMetadata_OmittedIsPrivate_PreservesExistingPrivateVisibility() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Old Title");
        list.setIsPrivate(true);
        list.setLicence(null);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "New Title", "description", null, "TEST",
                "authority", "region", null, null, false, false,
                false, false, false, false, new ArrayList<>(), "", principal);

        assertNotNull(result);
        assertTrue(result.getIsPrivate());
        assertNull(result.getLicence());
        verify(speciesListMongoRepository).save(list);
    }

    @Test
    void testUpdateMetadata_OmittedIsPrivate_PreservesExistingPublicVisibility_RequiresLicence() {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Public List");
        list.setIsPrivate(false);
        list.setLicence(null);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);

        Exception exception = assertThrows(Exception.class, () -> {
            graphQLController.updateMetadata(
                    listId, "Public List", "description", null, "TEST",
                    "authority", "region", null, null, false, false,
                    false, false, false, false, new ArrayList<>(), "", principal);
        });

        assertEquals("A valid licence is required for public lists", exception.getMessage());
        verify(speciesListMongoRepository, never()).save(any());
    }

    @Test
    void testUpdateMetadata_LicenceWhitespace_NormalizedAndNoFalseReindex() throws Exception {
        String listId = "list123";
        SpeciesList list = new SpeciesList();
        list.setId(listId);
        list.setTitle("Old Title");
        list.setListType("TEST");
        list.setIsPrivate(true);
        list.setLicence(null);
        list.setIsAuthoritative(false);
        list.setIsSDS(false);
        list.setIsBIE(false);
        list.setIsBiosecurity(false);
        list.setIsInvasive(false);
        list.setIsThreatened(false);

        when(speciesListMongoRepository.findByIdOrDataResourceUid(listId, listId))
                .thenReturn(Optional.of(list));
        when(authUtils.isAuthorized(list, principal)).thenReturn(true);
        when(validationService.isValueValid(eq(ConstraintType.listType), any())).thenReturn(true);
        when(speciesListMongoRepository.save(any(SpeciesList.class))).thenAnswer(i -> i.getArgument(0));

        SpeciesList result = graphQLController.updateMetadata(
                listId, "Old Title", null, "   ", "TEST",
                null, null, null, true, false, false,
                false, false, false, false, null, "", principal);

        assertNotNull(result);
        assertNull(result.getLicence());
        verify(taxonService, never()).reindex(any());
        verify(speciesListMongoRepository).save(list);
    }
}
