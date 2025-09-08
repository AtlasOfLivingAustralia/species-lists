package au.org.ala.listsapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;

import au.org.ala.ws.security.AlaWebServiceAuthFilter;

@Configuration
@EnableWebSecurity
@ComponentScan(basePackages = { "au.org.ala.ws.security", "au.org.ala.security.common" })
@EnableMethodSecurity(securedEnabled = true)
@EnableCaching
@Order(1)
public class SecurityConfig {

    @Autowired
    protected AlaWebServiceAuthFilter alaWebServiceAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.addFilterBefore(alaWebServiceAuthFilter, BasicAuthenticationFilter.class);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/graphql", "/ingest", "/graphiql", "/v1/species/**", "/**") 
                .permitAll());

        /*
         * Configure security headers to address vulnerabilities:
         * - Fix Missing HTTP Strict Transport Security Policy (Medium severity)
         * - Fix Missing 'X-Frame-Options' Header (Low severity) - prevents clickjacking
         * - Fix Missing 'X-Content-Type-Options' Header (Low severity) - prevents MIME
         * sniffing
         * - Fix Missing Content Security Policy (Low severity) - prevents XSS attacks
         */
        http.headers(headers -> headers
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                        .maxAgeInSeconds(31536000) // 1 year
                        .includeSubDomains(true)
                        .preload(true))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentTypeOptions(Customizer.withDefaults())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data: https:; " +
                                "font-src 'self' data:; " +
                                "connect-src 'self'; " +
                                "frame-ancestors 'none'; " +
                                "base-uri 'self'")));

        return http.csrf(AbstractHttpConfigurer::disable).build();
    }

    @Bean
    public HttpFirewall allowEncodedSlashHttpFirewall() {
        DefaultHttpFirewall firewall = new DefaultHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true); // Allows %2F
        // firewall.setAllowUrlEncodedPercent(true); // Allows %25 (use with caution)
        // firewall.setAllowSemicolon(true); // Allows ; in path (often needed for
        // matrix variables or if URLs naturally contain them)
        // firewall.setAllowUrlEncodedPeriod(true); // Allows %2E
        // Add any other specific allowances you've identified as necessary
        return firewall;
    }
}
