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

package au.org.ala.listsapi;

import java.io.IOException;
import java.util.List;

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
import org.springframework.security.web.firewall.StrictHttpFirewall;
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

    @Autowired
    protected AlaWebServiceAuthFilter alaWebServiceAuthFilter;

    @Value("${app.url}")
    private String appUrl;

    @Value("${app.cookie.domain:}")
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

    // 
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Explicitly use the origin. 
        // Note: If appUrl has a trailing slash (e.g. ...:5173/), remove it!
        configuration.setAllowedOrigins(List.of(appUrl)); 
        
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
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/v1/**", "/v2/**").permitAll() // Broad API Permit
                .requestMatchers("/", "/graphql", "/ingest", "/graphiql", "/csrf").permitAll()
                .anyRequest().authenticated()
        );

        http.cors(Customizer.withDefaults());
        
        // 2. CSRF Configuration
        boolean isSecure = appUrl.toLowerCase().startsWith("https");
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        
        repository.setCookieCustomizer(cookie -> {
            cookie.path("/");
            if (isSecure && cookieDomain != null && !cookieDomain.isEmpty()) {
                cookie.secure(true);
                cookie.domain(cookieDomain);
                cookie.sameSite("None");
            } else {
                cookie.secure(false);
                cookie.sameSite("Lax");
            }
        });

        http.csrf(csrf -> csrf
                .csrfTokenRepository(repository)
                .csrfTokenRequestHandler(requestHandler)
                .requireCsrfProtectionMatcher(request -> {
                    String path = request.getRequestURI();
                    String method = request.getMethod();

                    // TEMPORARY DEBUG LOGGING - TODO: Remove this block after verifying CSRF behavior
                    System.out.println("=== CSRF CHECK ===");
                    System.out.println("Path: " + path);
                    System.out.println("Method: " + method);
                    System.out.println("Context Path: " + request.getContextPath());
                    System.out.println("Servlet Path: " + request.getServletPath());
                    
                    // Skip CSRF for OPTIONS requests (CORS preflight)
                    if ("OPTIONS".equals(method)) {
                        return false;
                    }
                    
                    // Skip CSRF for API endpoints
                    if (path.startsWith("/v1/") || path.startsWith("/v2/") || 
                        path.equals("/graphql") || path.equals("/ingest")) {
                        return false;
                    }
                    
                    // Require CSRF for state-changing methods on other paths
                    return "POST".equals(method) || "PUT".equals(method) || 
                            "DELETE".equals(method) || "PATCH".equals(method);
                })
        );

        // 3. Force the cookie for UI requests ONLY
        http.addFilterAfter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                
                String path = request.getRequestURI();
                // Skip CSRF token generation for API endpoints and exempt paths
                boolean isApi = path.startsWith("/v1/") || path.startsWith("/v2/") || 
                                path.equals("/graphql") || path.equals("/ingest");
                
                if (!isApi) {
                    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                    if (csrfToken != null) {
                        csrfToken.getToken();
                    }
                }
                filterChain.doFilter(request, response);
            }
        }, BasicAuthenticationFilter.class);

        // 4. Security Headers (Restored)
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

    /**
     * A StrictHttpFirewall that allows encoded slashes and semicolons in the URL.
     * 
     * @return
     */
    @Bean
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        // Note the 'UrlEncoded' part of the method name
        firewall.setAllowUrlEncodedDoubleSlash(true); 
        firewall.setAllowSemicolon(true);
        return firewall;
    }
}