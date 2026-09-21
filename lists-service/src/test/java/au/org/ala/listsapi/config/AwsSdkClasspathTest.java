package au.org.ala.listsapi.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Smoke test that instantiates AWS SDK clients to surface classpath/version
 * mismatches (e.g. missing transitive modules, broken service-loader entries)
 * locally, without any network calls.
 */
class AwsSdkClasspathTest {

    private static final StaticCredentialsProvider TEST_CREDENTIALS =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    @Test
    @DisplayName("S3, STS and S3Presigner clients can be instantiated from the classpath")
    void awsClients_instantiateWithoutErrors() {
        assertDoesNotThrow(() -> {
            try (SdkHttpClient http = UrlConnectionHttpClient.builder().build();
                 S3Client s3 = S3Client.builder()
                        .region(Region.AP_SOUTHEAST_2)
                        .credentialsProvider(TEST_CREDENTIALS)
                        .httpClient(http)
                        .build();
                 StsClient sts = StsClient.builder()
                        .region(Region.AP_SOUTHEAST_2)
                        .credentialsProvider(TEST_CREDENTIALS)
                        .httpClient(http)
                        .build();
                 S3Presigner presigner = S3Presigner.builder()
                        .region(Region.AP_SOUTHEAST_2)
                        .credentialsProvider(TEST_CREDENTIALS)
                        .build()) {
                // no-op: instantiation alone is the assertion
            }
        });
    }
}
