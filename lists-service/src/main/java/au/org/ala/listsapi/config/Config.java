package au.org.ala.listsapi.config;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.client.elc.rest5_client.Rest5Clients;
import org.springframework.web.client.RestTemplate;

@Configuration
public class Config extends ElasticsearchConfiguration {

  @Value("${elastic.host}")
  private String elasticHost;

  @Value("${elastic.username}")
  private String elasticUsername;

  @Value("${elastic.password}")
  private String elasticPassword;

  @Value("${elastic.auth.enabled:false}")
  private boolean elasticAuthEnabled;

  @Value("${elastic.tls.enabled:true}")
  private boolean elasticTlsEnabled;

  @Override
  public ClientConfiguration clientConfiguration() {
    ClientConfiguration.MaybeSecureClientConfigurationBuilder maybeSecureBuilder = ClientConfiguration.builder().connectedTo(elasticHost);

    ClientConfiguration.TerminalClientConfigurationBuilder terminalBuilder;

    if (elasticTlsEnabled) {
      try {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                  public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                  public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                  public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        }, new SecureRandom());

        terminalBuilder = maybeSecureBuilder
            .usingSsl(sslContext, NoopHostnameVerifier.INSTANCE)
            .withClientConfigurer(
                Rest5Clients.ElasticsearchConnectionManagerCallback.from(
                    cmBuilder -> cmBuilder.setTlsStrategy(
                        new DefaultClientTlsStrategy(
                            sslContext,
                            HostnameVerificationPolicy.CLIENT,
                            NoopHostnameVerifier.INSTANCE
                        )
                    )
                )
            );
      } catch (Exception e) {
        throw new RuntimeException("Failed to configure SSL context", e);
      }
    } else {
      terminalBuilder = ClientConfiguration.builder().connectedTo(elasticHost).withSocketTimeout(20000);
    }

    if (elasticAuthEnabled) {
      terminalBuilder = terminalBuilder.withBasicAuth(elasticUsername, elasticPassword);
    }

    terminalBuilder = terminalBuilder.withClientConfigurer(
        Rest5Clients.ElasticsearchHttpClientConfigurationCallback.from(
            httpClientBuilder -> httpClientBuilder.addRequestInterceptorLast((request, entity, context) -> {
                Header accept = request.getFirstHeader("Accept");
                if (accept != null && accept.getValue() != null && accept.getValue().contains("compatible-with=9")) {
                    request.setHeader("Accept", accept.getValue().replace("compatible-with=9", "compatible-with=8"));
                }
                Header contentType = request.getFirstHeader("Content-Type");
                if (contentType != null && contentType.getValue() != null && contentType.getValue().contains("compatible-with=9")) {
                    request.setHeader("Content-Type", contentType.getValue().replace("compatible-with=9", "compatible-with=8"));
                }
            })
        )
    );

    return terminalBuilder
            .withSocketTimeout(20000)
            .build();
  }


  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
