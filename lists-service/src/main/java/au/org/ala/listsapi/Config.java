package au.org.ala.listsapi;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
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
        ClientConfiguration.MaybeSecureClientConfigurationBuilder maybeSecureBuilder = ClientConfiguration.builder()
                .connectedTo(elasticHost);

        ClientConfiguration.TerminalClientConfigurationBuilder terminalBuilder;

        if (elasticTlsEnabled) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] {
                        new X509TrustManager() {
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                }, new SecureRandom());

                HostnameVerifier allowAllHosts = (hostname, session) -> true;
                terminalBuilder = maybeSecureBuilder.usingSsl(sslContext, allowAllHosts);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL context", e);
            }
        } else {
            terminalBuilder = ClientConfiguration.builder().connectedTo(elasticHost).withSocketTimeout(20000);
        }

        if (elasticAuthEnabled) {
            terminalBuilder = terminalBuilder.withBasicAuth(elasticUsername, elasticPassword);
        }

        return terminalBuilder
                .withSocketTimeout(20000)
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
