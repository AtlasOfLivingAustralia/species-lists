package au.org.ala.listsapi;

import au.org.ala.ws.security.AlaWebServiceAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@ComponentScan(basePackages = {"au.org.ala.ws.security"})
@EnableGlobalMethodSecurity(securedEnabled = true)
@EnableCaching
@Order(1)
public class SecurityConfig {

  @Autowired protected AlaWebServiceAuthFilter alaWebServiceAuthFilter;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http.addFilterBefore(alaWebServiceAuthFilter, BasicAuthenticationFilter.class);
    http.authorizeRequests()
        .requestMatchers("/", "/graphql", "/ingest", "/graphiql", "/**")
        .permitAll();
    return http.csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.disable()).build();
  }
}
