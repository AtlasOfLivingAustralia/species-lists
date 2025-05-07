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
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;

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
            .requestMatchers("/", "/graphql", "/ingest", "/graphiql", "/v1/species/**", "/**")
            .permitAll();
    return http.csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.disable()).build();
  }

  @Bean
  public HttpFirewall allowEncodedSlashHttpFirewall() {
    DefaultHttpFirewall firewall = new DefaultHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true);     // Allows %2F
    // firewall.setAllowUrlEncodedPercent(true);  // Allows %25 (use with caution)
    // firewall.setAllowSemicolon(true);          // Allows ; in path (often needed for matrix variables or if URLs naturally contain them)
    // firewall.setAllowUrlEncodedPeriod(true);   // Allows %2E
    // Add any other specific allowances you've identified as necessary
    return firewall;
  }
}
