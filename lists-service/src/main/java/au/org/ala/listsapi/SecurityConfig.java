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

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

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
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE); 
        return registrationBean;
    }

    /**
     * Debug filter to log ALL requests - helps diagnose issues in EKS
     * Remove this in production once issues are resolved
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> debugFilterRegistrationBean() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new OncePerRequestFilter() {
            private final Logger filterLog = LoggerFactory.getLogger("DebugFilter");
            
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                          FilterChain filterChain) throws ServletException, IOException {
                filterLog.info("====== DEBUG FILTER ======");
                filterLog.info("Request URI: {}", request.getRequestURI());
                filterLog.info("Request URL: {}", request.getRequestURL());
                filterLog.info("Method: {}", request.getMethod());
                filterLog.info("Context Path: {}", request.getContextPath());
                filterLog.info("Servlet Path: {}", request.getServletPath());
                filterLog.info("Query String: {}", request.getQueryString());
                filterLog.info("Remote Addr: {}", request.getRemoteAddr());
                filterLog.info("==========================");
                
                try {
                    filterChain.doFilter(request, response);
                    filterLog.info("Response Status: {}", response.getStatus());
                } catch (Exception e) {
                    filterLog.error("Exception in filter chain: {}", e.getMessage(), e);
                    throw e;
                }
            }
        });
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE - 1);
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
        
        // Debug filter to log authorization decisions
        http.addFilterBefore(new OncePerRequestFilter() {
            private final Logger authLog = LoggerFactory.getLogger("AuthorizationCheck");
            
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                          FilterChain filterChain) throws ServletException, IOException {
                String path = request.getRequestURI();
                String method = request.getMethod();
                
                authLog.info("=== AUTHORIZATION CHECK ===");
                authLog.info("Path: {}", path);
                authLog.info("Method: {}", method);
                authLog.info("Auth header: {}", request.getHeader("Authorization"));
                authLog.info("User Principal: {}", request.getUserPrincipal());
                
                filterChain.doFilter(request, response);
            }
        }, AlaWebServiceAuthFilter.class);
        
        // 1. Authorization Configuration
        http.authorizeHttpRequests(auth -> {
            log.info("=== Configuring Authorization Rules ===");
            auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/v1/**", "/v2/**").permitAll()
                .requestMatchers("/", "/graphql", "/ingest", "/graphiql", "/csrf").permitAll()
                .anyRequest().permitAll();
        });

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
                    Logger csrfLog = LoggerFactory.getLogger("CsrfCheck");
                    
                    csrfLog.info("=== CSRF CHECK ===");
                    csrfLog.info("Path: {}", path);
                    csrfLog.info("Method: {}", method);
                    
                    // Skip CSRF for OPTIONS requests (CORS preflight)
                    if ("OPTIONS".equals(method)) {
                        csrfLog.info("Result: SKIP (OPTIONS)");
                        return false;
                    }
                    
                    // Skip CSRF for API endpoints
                    if (path.startsWith("/v1/") || path.startsWith("/v2/") || 
                        path.equals("/graphql") || path.equals("/ingest")) {
                        csrfLog.info("Result: SKIP (API endpoint)");
                        return false;
                    }
                    
                    // Require CSRF for state-changing methods on other paths
                    boolean requiresCsrf = "POST".equals(method) || "PUT".equals(method) || 
                           "DELETE".equals(method) || "PATCH".equals(method);
                    csrfLog.info("Result: {}", requiresCsrf ? "REQUIRE CSRF" : "SKIP");
                    return requiresCsrf;
                })
        );

        // 3. Force the cookie for UI requests ONLY
        http.addFilterAfter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                
                String path = request.getRequestURI();
                boolean isApi = path.startsWith("/v1/") || path.startsWith("/v2/");
                
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
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedDoubleSlash(true); 
        firewall.setAllowSemicolon(true);
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowBackSlash(true);
        firewall.setAllowUrlEncodedPeriod(true);
        return firewall;
    }
}