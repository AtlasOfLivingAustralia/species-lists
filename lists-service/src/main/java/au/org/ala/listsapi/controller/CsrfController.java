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
package au.org.ala.listsapi.controller;  

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;  

/**
 * 
 * A simple controller to expose an endpoint for CSRF token generation.
 * The actual CSRF token handling is done by Spring Security filters.
 * This endpoint can be called by the client to initiate a CSRF token
 * being set in a cookie.
 * 
 * @author Nick dos Remedios <nick.dosremedios at atlas.org.au>
 */
@RestController  
public class CsrfController {  
    
    @GetMapping("/csrf")  
    public void setupCsrf(HttpServletRequest request, HttpServletResponse response) {
        // This attribute is set by Spring Security's CsrfFilter
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // IMPORTANT: Calling .getToken() "resolves" the deferred token
            // and forces the Repository to write the Set-Cookie header.
            token.getToken(); 
        }
    }
}