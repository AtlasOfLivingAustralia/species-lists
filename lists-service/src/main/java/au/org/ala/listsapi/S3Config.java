package au.org.ala.listsapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sts.StsClient;

@Configuration
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true")
public class S3Config {

    @Value("${aws.s3.region:ap-southeast-2}")
    private String region;

    @Bean
    public SdkHttpClient httpClient() {
        return UrlConnectionHttpClient.builder().build();
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        // Set system property to resolve HTTP client implementation conflict
        System.setProperty("software.amazon.awssdk.http.service.impl",
                          "software.amazon.awssdk.http.urlconnection.UrlConnectionSdkHttpService");

        String webIdentityTokenFile = System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE");
        String roleArn = System.getenv("AWS_ROLE_ARN");

        if (webIdentityTokenFile != null && roleArn != null) {
            return WebIdentityTokenFileCredentialsProvider.builder().build();
        } else {
            return DefaultCredentialsProvider.create();
        }
    }

    @Bean
    public StsClient stsClient(SdkHttpClient httpClient, AwsCredentialsProvider credentialsProvider) {
        return StsClient.builder()
                .region(Region.of(region))
                .httpClient(httpClient)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public S3Client s3Client(SdkHttpClient httpClient, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .httpClient(httpClient)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}