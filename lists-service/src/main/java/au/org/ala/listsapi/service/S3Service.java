package au.org.ala.listsapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Value("${aws.s3.tempBucket}")
    private String tempBucket;

    @Value("${aws.s3.uploadExpiry:PT1H}")
    private Duration uploadExpiry;

    /**
     * Upload a MultipartFile to S3 and return the S3 key
     */
    public String uploadFile(MultipartFile file) throws IOException {
        String key = generateUniqueKey(file.getOriginalFilename());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("original-filename", file.getOriginalFilename());
        metadata.put("content-type", file.getContentType());
        metadata.put("upload-timestamp", String.valueOf(System.currentTimeMillis()));

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(tempBucket)
                .key(key)
                .contentType(file.getContentType())
                .metadata(metadata)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        logger.debug("File uploaded successfully to S3: {}", key);
        return key;
    }

    /**
     * Upload an InputStream to S3 and return the S3 key
     */
    public String uploadFile(InputStream inputStream, String filename, String contentType, long contentLength) {
        String key = generateUniqueKey(filename);

        logger.debug("Uploading stream to S3: bucket={}, key={}, size={}",
            tempBucket, key, contentLength);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("original-filename", filename);
        metadata.put("content-type", contentType);
        metadata.put("upload-timestamp", String.valueOf(System.currentTimeMillis()));

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(tempBucket)
                .key(key)
                .contentType(contentType)
                .metadata(metadata)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

        logger.debug("Stream uploaded successfully to S3: {}", key);
        return key;
    }

    /**
     * Get an InputStream for a file from S3
     */
    public Optional<InputStream> getFileStream(String key) {
        try {
            logger.debug("Retrieving file from S3: bucket={}, key={}", tempBucket, key);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(tempBucket)
                    .key(key)
                    .build();

            InputStream inputStream = s3Client.getObject(getObjectRequest);
            logger.debug("File retrieved successfully from S3: {}", key);
            return Optional.of(inputStream);

        } catch (NoSuchKeyException e) {
            logger.warn("File not found in S3: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error retrieving file from S3: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * Get file metadata from S3
     */
    public Optional<HeadObjectResponse> getFileMetadata(String key) {
        try {
            logger.debug("Retrieving file metadata from S3: bucket={}, key={}", tempBucket, key);

            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(tempBucket)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            logger.debug("File metadata retrieved successfully from S3: {}", key);
            return Optional.of(response);

        } catch (NoSuchKeyException e) {
            logger.warn("File not found in S3: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error retrieving file metadata from S3: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * Delete a file from S3
     */
    public boolean deleteFile(String key) {
        try {
            logger.debug("Deleting file from S3: bucket={}, key={}", tempBucket, key);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(tempBucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            logger.debug("File deleted successfully from S3: {}", key);
            return true;

        } catch (Exception e) {
            logger.error("Error deleting file from S3: {}", key, e);
            return false;
        }
    }

    /**
     * Check if a file exists in S3
     */
    public boolean fileExists(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(tempBucket)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            logger.error("Error checking file existence in S3: {}", key, e);
            return false;
        }
    }

    /**
     * Generate a pre-signed URL for direct upload to S3
     */
    public String generatePresignedUploadUrl(String filename, String contentType) {
        String key = generateUniqueKey(filename);

        logger.debug("Generating pre-signed upload URL: bucket={}, key={}", tempBucket, key);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("original-filename", filename);
        metadata.put("content-type", contentType);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(tempBucket)
                .key(key)
                .contentType(contentType)
                .metadata(metadata)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(uploadExpiry)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        logger.debug("Pre-signed URL generated successfully for key: {}", key);
        return presignedRequest.url().toString();
    }

    /**
     * Generate a unique S3 key for a filename
     */
    private String generateUniqueKey(String originalFilename) {
        String sanitizedFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "uploads/" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString() + "-" + sanitizedFilename;
    }

    /**
     * Extract the original filename from S3 metadata
     */
    public String getOriginalFilename(String key) {
        Optional<HeadObjectResponse> metadata = getFileMetadata(key);
        if (metadata.isPresent() && metadata.get().metadata().containsKey("original-filename")) {
            return metadata.get().metadata().get("original-filename");
        }
        // Fallback: extract from key
        String[] parts = key.split("-");
        return parts.length > 2 ? parts[parts.length - 1] : "unknown";
    }

    /**
     * Get content type from S3 metadata
     */
    public String getContentType(String key) {
        Optional<HeadObjectResponse> metadata = getFileMetadata(key);
        if (metadata.isPresent()) {
            String contentType = metadata.get().contentType();
            if (contentType != null && !contentType.isEmpty()) {
                return contentType;
            }
            // Fallback to metadata
            if (metadata.get().metadata().containsKey("content-type")) {
                return metadata.get().metadata().get("content-type");
            }
        }
        return "application/octet-stream";
    }
}