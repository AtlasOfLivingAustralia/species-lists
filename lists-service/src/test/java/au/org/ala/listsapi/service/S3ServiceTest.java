package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @InjectMocks
    private S3Service s3Service;

    @Mock
    private MultipartFile mockFile;

    @Test
    void detectContentType_whenFileHasSpecificContentType_returnsIt() throws IOException {
        // Arrange
        when(mockFile.getContentType()).thenReturn("text/csv");

        // Act
        String contentType = s3Service.detectContentType(mockFile);

        // Assert
        assertEquals("text/csv", contentType);
        // Verify that getInputStream is not called when content type is present
        verify(mockFile, never()).getInputStream();
    }

    @Test
    void detectContentType_whenFileContentTypeIsNull_usesTika() throws IOException {
        // Arrange
        String filename = "test.csv";
        String content = "header1,header2\nvalue1,value2";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());

        when(mockFile.getContentType()).thenReturn(null);
        when(mockFile.getInputStream()).thenReturn(inputStream);
        when(mockFile.getOriginalFilename()).thenReturn(filename);

        // Act
        String contentType = s3Service.detectContentType(mockFile);

        // Assert
        // Tika should detect this as text/csv
        assertEquals("text/csv", contentType);
    }

    @Test
    void detectContentType_whenFileContentTypeIsGeneric_usesTika() throws IOException {
        // Arrange
        String filename = "test.json";
        String content = "{\"key\":\"value\"}";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());

        when(mockFile.getContentType()).thenReturn("application/octet-stream");
        when(mockFile.getInputStream()).thenReturn(inputStream);
        when(mockFile.getOriginalFilename()).thenReturn(filename);

        // Act
        String contentType = s3Service.detectContentType(mockFile);

        // Assert
        // Tika should detect this as application/json
        assertEquals("application/json", contentType);
    }

    @Test
    void detectContentType_whenGetInputStreamThrowsIOException_propagatesException() throws IOException {
        // Arrange
        when(mockFile.getContentType()).thenReturn(null);
        when(mockFile.getInputStream()).thenThrow(new IOException("Test exception"));

        // Act & Assert
        assertThrows(IOException.class, () -> s3Service.detectContentType(mockFile));
    }

    @Test
    void detectContentType_whenFileHasNoExtensionAndContentTypeIsNull_usesTika() throws IOException {
        // Arrange
        String filename = "myfile";
        String content = "some plain text";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());

        when(mockFile.getContentType()).thenReturn(null);
        when(mockFile.getInputStream()).thenReturn(inputStream);
        when(mockFile.getOriginalFilename()).thenReturn(filename);

        // Act
        String contentType = s3Service.detectContentType(mockFile);

        // Assert
        // Tika should detect this as text/plain
        assertEquals("text/plain", contentType);
    }
}
