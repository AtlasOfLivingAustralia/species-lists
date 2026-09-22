package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import au.org.ala.listsapi.model.InputSpeciesList;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.service.S3Service;
import au.org.ala.listsapi.service.UploadService;
import au.org.ala.listsapi.service.ValidationService;
import au.org.ala.ws.security.profile.AlaUserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IngressControllerTest {

    @Mock private UploadService uploadService;
    @Mock private ValidationService validationService;
    @Mock private AuthUtils authUtils;
    @Mock private S3Service s3Service;
    @Mock private AlaUserProfile alaUserProfile;

    @InjectMocks private IngressController ingressController;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ingressController, "s3Enabled", true);
    }

    @Test
    void ingest_nonAdminWithBiosecurity_returnsForbidden() throws Exception {
        InputSpeciesList input = new InputSpeciesList();
        input.setTitle("Biosecurity List");
        input.setIsBiosecurity("true");

        when(authUtils.isAdmin(alaUserProfile)).thenReturn(false);

        ResponseEntity<Object> response = ingressController.ingest("file-123", input, alaUserProfile);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("isBiosecurity"));
        verify(uploadService, never()).ingest(any(), any(), any(), eq(false));
    }

    @Test
    void ingest_nonAdminWithMultipleAdminFlags_returnsForbidden() throws Exception {
        InputSpeciesList input = new InputSpeciesList();
        input.setTitle("Admin Flags List");
        input.setIsBiosecurity("true");
        input.setIsAuthoritative("true");

        when(authUtils.isAdmin(alaUserProfile)).thenReturn(false);

        ResponseEntity<Object> response = ingressController.ingest("file-123", input, alaUserProfile);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("isBiosecurity"));
        assertTrue(response.getBody().toString().contains("isAuthoritative"));
        verify(uploadService, never()).ingest(any(), any(), any(), eq(false));
    }

    @Test
    void ingest_adminWithBiosecurity_success() throws Exception {
        InputSpeciesList input = new InputSpeciesList();
        input.setTitle("Biosecurity List");
        input.setIsBiosecurity("true");

        when(authUtils.isAdmin(alaUserProfile)).thenReturn(true);
        when(validationService.isListValid(input)).thenReturn(true);
        when(s3Service.fileExists("file-123")).thenReturn(true);

        SpeciesList createdList = new SpeciesList();
        createdList.setTitle("Biosecurity List");
        createdList.setIsBiosecurity(true);
        when(uploadService.ingest(alaUserProfile, input, "file-123", false)).thenReturn(createdList);

        ResponseEntity<Object> response = ingressController.ingest("file-123", input, alaUserProfile);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(createdList, response.getBody());
        verify(uploadService).ingest(alaUserProfile, input, "file-123", false);
    }

    @Test
    void ingest_nonAdminWithoutAdminFlags_success() throws Exception {
        InputSpeciesList input = new InputSpeciesList();
        input.setTitle("Regular List");
        input.setIsPrivate("true");

        when(authUtils.isAdmin(alaUserProfile)).thenReturn(false);
        when(validationService.isListValid(input)).thenReturn(true);
        when(s3Service.fileExists("file-123")).thenReturn(true);

        SpeciesList createdList = new SpeciesList();
        createdList.setTitle("Regular List");
        when(uploadService.ingest(alaUserProfile, input, "file-123", false)).thenReturn(createdList);

        ResponseEntity<Object> response = ingressController.ingest("file-123", input, alaUserProfile);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(createdList, response.getBody());
        verify(uploadService).ingest(alaUserProfile, input, "file-123", false);
    }
}
