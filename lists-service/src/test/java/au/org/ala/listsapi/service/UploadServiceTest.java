package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock private SpeciesListItemMongoRepository speciesListItemMongoRepository;
    @Mock private SpeciesListMongoRepository speciesListMongoRepository;
    @Mock private SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
    @Mock private TaxonService taxonService;
    @Mock private ReleaseService releaseService;
    @Mock private MetadataService metadataService;
    @Mock private ProgressService progressService;
    @Mock private SearchHelperService searchHelperService;

    @InjectMocks private UploadService uploadService;

    @BeforeEach
    void setUp() {
        // Some mocks might be needed
    }

    @Test
    void testLoadCSVWithFallback_Windows1252() throws Exception {
        // CSV with invalid UTF-8 (CP1252 quote)
        byte[] badUtf8 = new byte[] {
            'n', 'a', 'm', 'e', ',', 'v', 'a', 'l', 'u', 'e', '\n',
            'b', 'a', 'd', (byte)0x92, 'c', 'h', 'a', 'r', ',', '1'
        }; 
        
        when(speciesListMongoRepository.findById("testList")).thenReturn(Optional.of(new au.org.ala.listsapi.model.SpeciesList()));

        IngestJob job = uploadService.loadCSVWithFallback(
                "testList",
                () -> new ByteArrayInputStream(badUtf8),
                false, // not dryRun to trigger db interactions (which are mocked)
                true,  // skipIndexing true to avoid some interactions
                false
        );

        // Expect 1 row
        assertEquals(1, job.getRowCount());
        
        // It should have caught the charset error and cleaned up the DB
        verify(speciesListItemMongoRepository, times(1)).deleteBySpeciesListID("testList");
        verify(speciesListIndexElasticRepository, times(1)).deleteSpeciesListItemBySpeciesListID("testList");
        
        // And the facet list should be extracted correctly based on the windows-1252 char
        assertTrue(job.getFieldList().contains("value"));
    }

    @Test
    void testLoadCSV_EmptyFacetNamesAreFiltered() throws Exception {
        // CSV with an empty column header
        String csv = "name,,\nvalue1,value2,";
        
        when(speciesListMongoRepository.findById("testList")).thenReturn(Optional.of(new au.org.ala.listsapi.model.SpeciesList()));

        IngestJob job = uploadService.loadCSVWithFallback(
                "testList",
                () -> new ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                false,
                true,
                false
        );

        // Expect 1 row
        assertEquals(1, job.getRowCount());
        
        // Should not contain empty string in facets or fields
        for (String field : job.getFieldList()) {
            assertTrue(field != null && !field.trim().isEmpty(), "Field list should not contain empty strings");
        }
        for (String facet : job.getFacetList()) {
            assertTrue(facet != null && !facet.trim().isEmpty(), "Facet list should not contain empty strings");
        }
    }
}
