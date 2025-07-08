package au.org.ala.listsapi;

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

  @Override
  public ClientConfiguration clientConfiguration() {
    ClientConfiguration.TerminalClientConfigurationBuilder builder = ClientConfiguration.builder().connectedTo(elasticHost).withSocketTimeout(20000);
    if (elasticAuthEnabled) {
      builder = builder.withBasicAuth(elasticUsername, elasticPassword);
    }
    return builder.build();
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
