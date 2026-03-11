/**
 * Copyright (c) 2025 Atlas of Living Australia
 * All Rights Reserved.
 * 
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.listsapi.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.support.MultipartFilter;

import au.org.ala.ws.security.AlaWebServiceAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@ComponentScan(basePackages = { "au.org.ala.ws.security", "au.org.ala.security.common" })
@EnableMethodSecurity(securedEnabled = true)
@EnableCaching
@Order(1)
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    private static final Pattern DOMAIN_NAME_PATTERN = Pattern.compile(
            "^(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\\.(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*$");

    @Autowired
    protected AlaWebServiceAuthFilter alaWebServiceAuthFilter;

    @Value("${app.url}")
    private String appUrl;

    @Value("${cors.domain}")
    private String corsDomain; 

    @Value("${app.cookie.domain}")
    private String cookieDomain;

    @Bean
    public FilterRegistrationBean<MultipartFilter> multipartFilterRegistrationBean() {
        FilterRegistrationBean<MultipartFilter> registrationBean = new FilterRegistrationBean<>();
        MultipartFilter multipartFilter = new MultipartFilter();
        registrationBean.setFilter(multipartFilter);
        // This ensures it runs before the Spring Security Filter Chain
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE); 
        return registrationBean;
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Note: If appUrl has a trailing slash (e.g. ...:5173/), remove it!
        // Multiple origins can be comma-separated; cors.domain adds a wildcard subdomain pattern
        List<String> allowedOrigins = new ArrayList<>(Arrays.asList(appUrl.split(",\\s*")));
        if (isValidDomain(corsDomain)) {
            allowedOrigins.add("https://*." + corsDomain.trim());
        } else if (corsDomain != null && !corsDomain.isBlank()) {
            logger.warn("Ignoring invalid cors.domain value: {}", corsDomain);
        }
        configuration.setAllowedOriginPatterns(allowedOrigins);
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization", 
            "Content-Type", 
            "X-XSRF-TOKEN", 
            "Accept", 
            "X-Requested-With"
        ));
        
        configuration.setAllowCredentials(true); 
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        http.addFilterBefore(alaWebServiceAuthFilter, BasicAuthenticationFilter.class);
        
        // 1. Authorization Configuration
        http.authorizeHttpRequests(auth -> auth
            // ALLOW ALL OPTIONS REQUESTS (The fix for 403 Preflight)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/graphql", "/ingest", "/graphiql", "/v1/species/**", "/csrf", "/**")
                .permitAll());
        http.cors(Customizer.withDefaults());
        
        // 2. CSRF Configuration (Updated for SPA/React)
        boolean isSecure = Arrays.stream(appUrl.split(",\\s*")).anyMatch(url -> url.toLowerCase().startsWith("https"));
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        // If EKS is HTTPS, this MUST be true. 
        // If you're testing on HTTP, it must be false.
        repository.setCookieCustomizer(cookie -> {
            cookie.path("/");
            if (isSecure) {
                // EKS / Production settings
                cookie.secure(true);
                cookie.domain(cookieDomain); // e.g., "dev.ala.org.au" or "ala.org.au"
                cookie.sameSite("None");        // Required for cross-subdomain
            } else {
                // Localhost settings
                cookie.secure(false);
                // DO NOT set domain for localhost; let it default to null/host-only
                cookie.sameSite("Lax"); 
            }
        });

        http.csrf(csrf -> csrf
                .csrfTokenRepository(repository)
                .csrfTokenRequestHandler(requestHandler)
                .requireCsrfProtectionMatcher(request -> {
                    // Skip CSRF for server-to-server Bearer token requests
                    String auth = request.getHeader("Authorization");
                    if (auth != null && auth.startsWith("Bearer ")) {
                        return false;
                    }
                    // Keep CSRF for browser requests (all non-safe methods)
                    return !Set.of("GET", "HEAD", "TRACE", "OPTIONS")
                               .contains(request.getMethod());
                })
        );

        // 3. Force the cookie to be sent on every request so React can find it
        http.addFilterAfter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (csrfToken != null) {
                    // This triggers the actual generation of the token/cookie
                    csrfToken.getToken();
                }
                filterChain.doFilter(request, response);
            }
        }, BasicAuthenticationFilter.class);

        // 4. Security Headers
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

        return http.build();
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

    private boolean isValidDomain(String domain) {
        if (domain == null) {
            return false;
        }
        String trimmed = domain.trim();
        if (trimmed.isEmpty() || trimmed.length() > 253) {
            return false;
        }
        if (trimmed.startsWith(".") || trimmed.endsWith(".")) {
            return false;
        }
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            return false;
        }
        return DOMAIN_NAME_PATTERN.matcher(trimmed).matches();
    }
}
