package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import au.org.ala.listsapi.model.IngestJob;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock private Executor processExecutor;

    @InjectMocks private UploadService uploadService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(uploadService, "tempDir", System.getProperty("java.io.tmpdir"));
        ReflectionTestUtils.setField(uploadService, "s3Enabled", false);
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

    @Test
    void testReload_missingLocalFile_doesNotDeleteExistingItems() {
        SpeciesList speciesList = new SpeciesList();
        speciesList.setId("dr123");
        when(speciesListMongoRepository.findByIdOrDataResourceUid("dr123", "dr123"))
                .thenReturn(Optional.of(speciesList));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () ->
                        uploadService.reload("dr123", "missing-file.csv", false));

        assertTrue(ex.getMessage().contains("File not uploaded yet"));
        verify(speciesListIndexElasticRepository, never()).deleteSpeciesListItemBySpeciesListID(any());
        verify(speciesListItemMongoRepository, never()).deleteBySpeciesListID(any());
        verify(progressService, never()).clearIngestProgress(any());
    }

    @Test
    void testReload_unsupportedFileType_doesNotDeleteExistingItems() throws Exception {
        File tempFile = File.createTempFile("WildNet_2026", ".txt");
        tempFile.deleteOnExit();

        SpeciesList speciesList = new SpeciesList();
        speciesList.setId("dr123");
        when(speciesListMongoRepository.findByIdOrDataResourceUid("dr123", "dr123"))
                .thenReturn(Optional.of(speciesList));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () ->
                        uploadService.reload("dr123", tempFile.getName(), false));

        assertTrue(ex.getMessage().contains("Unsupported file type"));
        verify(speciesListIndexElasticRepository, never()).deleteSpeciesListItemBySpeciesListID(any());
        verify(speciesListItemMongoRepository, never()).deleteBySpeciesListID(any());
    }

    @Test
    void testReload_invalidFileIdentifier_doesNotDeleteExistingItems() {
        SpeciesList speciesList = new SpeciesList();
        speciesList.setId("dr123");
        when(speciesListMongoRepository.findByIdOrDataResourceUid("dr123", "dr123"))
                .thenReturn(Optional.of(speciesList));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () ->
                        uploadService.reload("dr123", "../etc/passwd", false));

        assertTrue(ex.getMessage().contains("Invalid file identifier"));
        verify(speciesListIndexElasticRepository, never()).deleteSpeciesListItemBySpeciesListID(any());
        verify(speciesListItemMongoRepository, never()).deleteBySpeciesListID(any());
    }

    @Test
    void testAsyncIngest_localNullContentType_throwsInsteadOfSilentlyFailing() throws Exception {
        // Simulate a file whose extension is supported but whose probeContentType would
        // have returned null. The new implementation determines type from the filename
        // so it should still succeed; this test guards against the historical NPE path.
        File tempFile = File.createTempFile("WildNet_2026", ".csv");
        tempFile.deleteOnExit();
        Files.writeString(
                tempFile.toPath(),
                "scientificName\nAcacia dealbata\n");

        SpeciesList speciesList = new SpeciesList();
        speciesList.setId("dr123");
        when(speciesListMongoRepository.findById("dr123"))
                .thenReturn(Optional.of(speciesList));

        uploadService.asyncIngest(speciesList, tempFile, false, true);

        assertEquals(Integer.valueOf(1), speciesList.getRowCount());
    }

    @Test
    void testAsyncIngest_localUnsupportedFileType_throwsClearError() throws Exception {
        File tempFile = File.createTempFile("WildNet_2026", ".txt");
        tempFile.deleteOnExit();

        SpeciesList speciesList = new SpeciesList();
        speciesList.setId("dr123");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () ->
                        uploadService.asyncIngest(speciesList, tempFile, false, true));

        assertTrue(ex.getMessage().contains("Unsupported file type"));
        assertEquals(null, speciesList.getRowCount());
    }

    @Test
    void testStartAsyncIngest_failure_reportsErrorToProgress() {
        SpeciesList speciesList = new SpeciesList();
        speciesList.setId("dr456");

        // Use a direct executor so the future completes synchronously in the test
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(processExecutor).execute(any());

        uploadService.startAsyncIngest(speciesList, "missing-file.csv", false);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(progressService, atLeastOnce()).addIngestError(any(), errorCaptor.capture());
        String captured = errorCaptor.getValue();
        assertNotNull(captured);
        assertTrue(
                captured.contains("FileNotFoundException")
                        || captured.contains("File not uploaded yet")
                        || captured.contains("File not found"));
    }
}
