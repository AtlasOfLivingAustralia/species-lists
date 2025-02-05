package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.ws.security.profile.AlaUserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DownloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpeciesListMongoRepository speciesListMongoRepository;

    @MockBean
    private SpeciesListItemMongoRepository speciesListItemMongoRepository;

    @MockBean
    private AuthUtils authUtils;

    private SpeciesList publicSpeciesList;
    private SpeciesList privateSpeciesList;
    private SpeciesListItem speciesListItem;

    @BeforeEach
    public void setUp() {
        // Initialize common objects
        publicSpeciesList = createSpeciesList("test-list-id", false);
        privateSpeciesList = createSpeciesList("private-list-id", true);
        speciesListItem = createSpeciesListItem("test-list-id");
    }

    private SpeciesList createSpeciesList(String id, boolean isPrivate) {
        SpeciesList speciesList = new SpeciesList();
        speciesList.setId(id);
        speciesList.setIsPrivate(isPrivate);
        speciesList.setFieldList(Arrays.asList("field1", "field2"));
        return speciesList;
    }

    private SpeciesListItem createSpeciesListItem(String speciesListID) {
        SpeciesListItem item = new SpeciesListItem();
        item.setSpeciesListID(speciesListID);
        item.setScientificName("Test species");
        item.setProperties(Arrays.asList(
                new KeyValue("field1", "value1"),
                new KeyValue("field2", "value2")
        ));

        // Set classification
        Classification classification = new Classification();
        classification.setTaxonConceptID("123");
        classification.setScientificName("Test species");
        classification.setGenus("Test genus");
        classification.setFamily("Test family");
        classification.setOrder("Test order");
        classification.setClasss("Test class");
        classification.setPhylum("Test phylum");
        classification.setKingdom("Test kingdom");
        classification.setVernacularName("Test common name");
        classification.setMatchType("exact");
        classification.setNameType("scientific");
        item.setClassification(classification);

        return item;
    }

    @Test
    public void testDownloadPublicSpeciesList() throws Exception {
        // Mock repositories to return the public species list and items
        when(speciesListMongoRepository.findByIdOrDataResourceUid(eq("test-list-id"), eq("test-list-id")))
                .thenReturn(Optional.of(publicSpeciesList));

        Page<SpeciesListItem> page = new PageImpl<>(Arrays.asList(speciesListItem));
        when(speciesListItemMongoRepository.findBySpeciesListIDOrderById(eq("test-list-id"), any(Pageable.class)))
                .thenReturn(page)
                .thenReturn(Page.empty());

        // Perform GET request
        MvcResult result = mockMvc.perform(get("/download/test-list-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andReturn();

        String content = result.getResponse().getContentAsString();

        // Assert that content contains CSV headers and data
        String expectedHeaders = "\"Supplied name\",\"field1\",\"field2\",\"guid\",\"scientificName\",\"genus\",\"family\",\"order\",\"class\",\"phylum\",\"kingdom\",\"vernacularName\",\"matchType\",\"nameType\"";
        assertTrue(content.contains(expectedHeaders));

        // Check that the CSV content includes the test data
        String expectedData = "\"Test species\",\"value1\",\"value2\",\"123\",\"Test species\",\"Test genus\",\"Test family\",\"Test order\",\"Test class\",\"Test phylum\",\"Test kingdom\",\"Test common name\",\"exact\",\"scientific\"";
        assertTrue(content.contains(expectedData));
    }

    @Test
    public void testDownloadNonExistentSpeciesList() throws Exception {
        // Mock repository to return empty Optional
        when(speciesListMongoRepository.findByIdOrDataResourceUid(eq("nonexistent-id"), eq("nonexistent-id")))
                .thenReturn(Optional.empty());

        // Perform GET request
        mockMvc.perform(get("/download/nonexistent-id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unrecognized ID while downloading dataset"));
    }

    @Test
    public void testDownloadPrivateSpeciesListUnauthorized() throws Exception {
        // Mock repositories
        when(speciesListMongoRepository.findByIdOrDataResourceUid(eq("private-list-id"), eq("private-list-id")))
                .thenReturn(Optional.of(privateSpeciesList));

        // User not logged in
        when(authUtils.getUserProfile(any(Principal.class))).thenReturn(null);

        // Perform GET request
        mockMvc.perform(get("/download/private-list-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("User not logged in"));
    }

    @Test
    @WithMockUser(username = "testuser")
    public void testDownloadPrivateSpeciesListAuthorized() throws Exception {
        // Mock repositories and authentication
        when(speciesListMongoRepository.findByIdOrDataResourceUid(eq("private-list-id"), eq("private-list-id")))
                .thenReturn(Optional.of(privateSpeciesList));

        AlaUserProfile userProfile = mock(AlaUserProfile.class);
        when(userProfile.getUsername()).thenReturn("testuser");

        when(authUtils.getUserProfile(any())).thenReturn(userProfile);
        when(authUtils.isAuthorized(eq(privateSpeciesList), any())).thenReturn(true);

        // Create a species list item for the private list
        SpeciesListItem privateItem = createSpeciesListItem("private-list-id");

        Page<SpeciesListItem> page = new PageImpl<>(Arrays.asList(privateItem));
        when(speciesListItemMongoRepository.findBySpeciesListIDOrderById(eq("private-list-id"), any(Pageable.class)))
                .thenReturn(page)
                .thenReturn(Page.empty());

        // Perform GET request
        MvcResult result = mockMvc.perform(get("/download/private-list-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andReturn();

        String content = result.getResponse().getContentAsString();

        // Assert that content contains CSV headers and data
        String expectedHeaders = "\"Supplied name\",\"field1\",\"field2\",\"guid\",\"scientificName\",\"genus\",\"family\",\"order\",\"class\",\"phylum\",\"kingdom\",\"vernacularName\",\"matchType\",\"nameType\"";
        assertTrue(content.contains(expectedHeaders));

        // Check that the CSV content includes the test data
        String expectedData = "\"Test species\",\"value1\",\"value2\",\"123\",\"Test species\",\"Test genus\",\"Test family\",\"Test order\",\"Test class\",\"Test phylum\",\"Test kingdom\",\"Test common name\",\"exact\",\"scientific\"";
        assertTrue(content.contains(expectedData));
    }

    @Test
    public void testDownloadWithZipParameter() throws Exception {
        // Mock repositories to return the public species list and items
        when(speciesListMongoRepository.findByIdOrDataResourceUid(eq("test-list-id"), eq("test-list-id")))
                .thenReturn(Optional.of(publicSpeciesList));

        Page<SpeciesListItem> page = new PageImpl<>(Arrays.asList(speciesListItem));
        when(speciesListItemMongoRepository.findBySpeciesListIDOrderById(eq("test-list-id"), any(Pageable.class)))
                .thenReturn(page)
                .thenReturn(Page.empty());

        // Perform GET request with zip parameter
        MvcResult result = mockMvc.perform(get("/download/test-list-id").param("zip", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=species-list-test-list-id.zip"))
                .andReturn();

        byte[] contentBytes = result.getResponse().getContentAsByteArray();

        // Assert that the ZIP content is not empty
        assertTrue(contentBytes.length > 0);
    }
}