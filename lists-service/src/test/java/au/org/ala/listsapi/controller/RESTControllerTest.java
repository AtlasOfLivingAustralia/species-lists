package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListCustomRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.BiocacheService;
import au.org.ala.listsapi.service.SearchHelperService;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@ExtendWith(MockitoExtension.class)
class RESTControllerTest {

    @Mock
    private SpeciesListMongoRepository speciesListMongoRepository;

    @Mock
    private SpeciesListCustomRepository speciesListCustomRepository;

    @Mock
    private BiocacheService biocacheService;

    @Mock
    private AuthUtils authUtils;

    @Mock
    private SearchHelperService searchHelperService;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private Principal principal;

    @InjectMocks
    private RESTController restController;

    private SpeciesList publicList;
    private SpeciesList privateList;

    @BeforeEach
    void setUp() {
        publicList = new SpeciesList();
        publicList.setId("public123");
        publicList.setIsPrivate(false);

        privateList = new SpeciesList();
        privateList.setId("private123");
        privateList.setIsPrivate(true);
    }

    @Test
    void testSpeciesList_FoundAndPublic() {
        when(speciesListMongoRepository.findByIdOrDataResourceUid("public123", "public123"))
                .thenReturn(Optional.of(publicList));

        ResponseEntity<SpeciesList> response = restController.speciesList("public123", principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("public123", response.getBody().getId());
    }

    @Test
    void testSpeciesList_NotFound() {
        when(speciesListMongoRepository.findByIdOrDataResourceUid("unknown", "unknown"))
                .thenReturn(Optional.empty());

        ResponseEntity<SpeciesList> response = restController.speciesList("unknown", principal);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void testSpeciesList_PrivateUnauthorized() {
        when(speciesListMongoRepository.findByIdOrDataResourceUid("private123", "private123"))
                .thenReturn(Optional.of(privateList));
        when(authUtils.isAuthorized(privateList, principal)).thenReturn(false);

        ResponseEntity<SpeciesList> response = restController.speciesList("private123", principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void testSpeciesList_PrivateAuthorized() {
        when(speciesListMongoRepository.findByIdOrDataResourceUid("private123", "private123"))
                .thenReturn(Optional.of(privateList));
        when(authUtils.isAuthorized(privateList, principal)).thenReturn(true);

        ResponseEntity<SpeciesList> response = restController.speciesList("private123", principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("private123", response.getBody().getId());
    }

    @Test
    void testSpeciesListQid_Found() throws Exception {
        when(speciesListMongoRepository.findByIdOrDataResourceUid("public123", "public123"))
                .thenReturn(Optional.of(publicList));
        when(biocacheService.getQidForSpeciesList("public123")).thenReturn("qid-12345");

        ResponseEntity<Object> response = restController.speciesListQid("public123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("qid-12345"));
    }

    @Test
    void testSpeciesListQid_NotFound() throws Exception {
        when(speciesListMongoRepository.findByIdOrDataResourceUid("unknown", "unknown"))
                .thenReturn(Optional.empty());

        ResponseEntity<Object> response = restController.speciesListQid("unknown");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
