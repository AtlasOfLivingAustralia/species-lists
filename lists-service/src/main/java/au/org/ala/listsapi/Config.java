package au.org.ala.listsapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class Config extends ElasticsearchConfiguration {

//  @Value("${elastic.host}")
//  private String elasticHost;

  @Override
  public ClientConfiguration clientConfiguration() {
    return ClientConfiguration.builder().connectedTo("vpc-os-provisioned-kbgh52sqbaulm6bvjm3vbfpyaa.ap-southeast-2.es.amazonaws.com:443").usingSsl().withSocketTimeout(20000).build();
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}