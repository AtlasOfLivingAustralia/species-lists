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

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.ws.security.profile.AlaUserProfile;

@Component
public class AuthUtils {
    Logger logger = Logger.getLogger(AuthUtils.class.getName());

    @Value("#{'${security.admin.role}'.split(',')}")
    private List<String> adminRoles;

    public AlaUserProfile getUserProfile(Principal principal) {
        AlaUserProfile profile = null;

        if (principal instanceof PreAuthenticatedAuthenticationToken) {
            profile = (AlaUserProfile) ((PreAuthenticatedAuthenticationToken) principal).getPrincipal();
        } else if (principal instanceof AlaUserProfile) {
            profile = (AlaUserProfile) principal;
        }

        return profile;
    }

    public boolean hasAdminRole(AlaUserProfile profile) {
        if (profile == null || adminRoles == null)
        return false;

        for (String role : profile.getRoles()) {
            if (adminRoles.contains(role)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasInternalScope(AlaUserProfile profile) {
        if (profile == null) {
            return false;
        }

        Set<String> roles = profile.getRoles();
        return roles != null && roles.contains("ala/internal");
    }

    public boolean isAuthenticated(Principal principal) {
        return getUserProfile(principal) != null;
    }

    public boolean isAuthorized(Principal principal) {
        AlaUserProfile profile = getUserProfile(principal);

        if (profile == null)
        return false;

        return (profile.getRoles() != null && (hasAdminRole(profile) || hasInternalScope(profile)));
    }

    public boolean isAuthorized(SpeciesList list, Principal principal) {
        // Principal needs to be one of the following:
        // 1) ROLE_ADMIN OR ROLE_EDITOR
        // 2) ROLE_USER and is the owner of the list
        // 3) ROLE_USER and an editor of the list
        // 4) M2M token with ala/internal scope
        AlaUserProfile profile = getUserProfile(principal);
        logger.info("User profile: " + profile);
        if (profile == null) {
            return false;
        }

        // Check for admin role, internal scope or editor role first (these don't require user ID)
        if (hasAdminRole(profile) || hasInternalScope(profile) || profile.getRoles().contains("ROLE_EDITOR")) {
            return true;
        }

        // For regular users, check ownership/editor status (requires user ID)
        String userId = profile.getUserId();
        if (userId == null) {
            return false; // Can't be owner or editor without a user ID
        }

        return (list.getOwner() != null && list.getOwner().equals(userId))
                || (list.getEditors() != null && list.getEditors().contains(userId));
    }
}
