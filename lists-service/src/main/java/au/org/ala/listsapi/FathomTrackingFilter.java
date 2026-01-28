/**
 * Copyright (c) 2026 Atlas of Living Australia
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class FathomTrackingFilter extends OncePerRequestFilter {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fathom.enabled:false}")
    private boolean fathomEnabled;
    
    @Value("${fathom.api.token:API_TOKEN_NOT_SET}")
    private String apiToken;

    @Value("${fathom.site.id:SITE_ID_NOT_SET}")
    private String siteId;

    @Value("${fathom.url:https://api.usefathom.com/v1}")
    private String fathomUrlString;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getServletPath();

        // Only track if the path starts with /v1 or /v2
        if (fathomEnabled && (path.startsWith("/v1") || path.startsWith("/v2"))) {
            
            // Distinguish usage via your custom header
            String internalHeader = request.getHeader("X-Internal-Source");
            String eventName = (internalHeader != null) ? "Internal Usage" : "External Usage";

            trackEvent(eventName);
        }

        filterChain.doFilter(request, response);
    }

    private void trackEvent(String name) {
        try {
            String url = String.format("%s/v1/events?site_id=%s&name=%s", fathomUrlString, siteId, name);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // This blocks the thread until Fathom responds
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            
        } catch (Exception e) {
            // Log it so you know if the token expires, but don't break the API
            logger.warn("Fathom logging skipped: " + e.getMessage());
        }
    }
}