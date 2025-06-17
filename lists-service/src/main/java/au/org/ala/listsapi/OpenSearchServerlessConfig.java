//package au.org.ala.listsapi;
//
//import org.apache.http.HttpHost;
//import org.apache.http.HttpRequestInterceptor;
//import org.elasticsearch.client.RestClient;
//import org.elasticsearch.client.RestClientBuilder;
//import org.elasticsearch.client.RestHighLevelClient;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
//import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.auth.signer.Aws4Signer;
//import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
//
//@Configuration
//public class OpenSearchServerlessConfig {
//
//    private static final String SERVICE_NAME = "aoss";  // OpenSearch uses "es"
//
//    @Bean
//    public RestHighLevelClient restHighLevelClient() {
//        String region = "ap-southeast-2";  // e.g., us-east-1
//        String openSearchEndpoint = "bb2thiz7u0y5o9h50b8.ap-southeast-2.aoss.amazonaws.com"; // without https://
//
//        HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(
//                SERVICE_NAME,
//                AwsV4HttpSigner.create(),
//                DefaultCredentialsProvider.create(),
//                Region.AP_SOUTHEAST_2
//        );
//
//        RestClientBuilder builder = RestClient.builder(new HttpHost(openSearchEndpoint, 443, "https"))
//                .setHttpClientConfigCallback(hc -> hc.addInterceptorLast(interceptor));
//
//        return new RestHighLevelClient(builder);
//    }
//}