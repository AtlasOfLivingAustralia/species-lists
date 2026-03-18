/*
 * Copyright (C) 2025 Atlas of Living Australia
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

import au.org.ala.ws.security.TokenClient;
import au.org.ala.ws.security.TokenInterceptor;
import au.org.ala.ws.security.TokenService;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.oidc.config.OidcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AlaWsPluginConfig {

    @Value("${webservice.client-id}")
    String clientId;

    @Value("${webservice.client-secret}")
    String clientSecret;

    @Value("${webservice.jwt-scopes}")
    String jwtScopes;

    @Value("${webservices.cache-tokens:true}")
    boolean cacheTokens;

    @Bean
    TokenClient tokenClient(@Autowired(required = false) OidcConfiguration oidcConfiguration) {
        return new TokenClient(oidcConfiguration);
    }

    @Bean
    TokenService tokenService(
            @Autowired(required = false) OidcConfiguration oidcConfiguration,
            @Autowired(required = false) SessionStore sessionStore,
            @Autowired TokenClient tokenClient) {
        // note not injecting PAC4j Config here due to potential circular dependency
        return new TokenService(
                oidcConfiguration,
                sessionStore,
                tokenClient,
                clientId,
                clientSecret,
                jwtScopes,
                cacheTokens);
    }

    /** OK HTTP Interceptor that injects a client credentials Bearer token into a request */
    @ConditionalOnProperty(prefix = "webservice", name = "jwt")
    @ConditionalOnMissingBean(name = "jwtInterceptor")
    @Bean
    TokenInterceptor jwtInterceptor(@Autowired TokenService tokenService) {
        return new TokenInterceptor(tokenService);
    }
}
