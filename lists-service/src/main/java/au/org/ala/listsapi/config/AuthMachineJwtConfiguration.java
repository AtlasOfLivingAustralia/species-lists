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

import org.pac4j.core.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import au.org.ala.listsapi.filter.AuthMachineJwt;
import au.org.ala.ws.security.client.AlaAuthClient;

@Configuration
public class AuthMachineJwtConfiguration {
    @Bean
    AuthMachineJwt authMachineJwt(Config config, AlaAuthClient alaAuthClient) {
        return new AuthMachineJwt(config, alaAuthClient);
    }
}
