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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.ws.security.profile.AlaUserProfile;

@Component
public class AuthUtils {

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

    public boolean isAuthenticated(Principal principal) {
        return getUserProfile(principal) != null;
    }

    public boolean isAuthorized(Principal principal) {
        AlaUserProfile profile = getUserProfile(principal);

        if (profile == null)
        return false;

        return (profile.getRoles() != null && hasAdminRole(profile));
    }

    public boolean isAuthorized(SpeciesList list, Principal principal) {
        // Principal needs to on of the following:
        // 1) ROLE_ADMIN
        // 2) ROLE_USER and is the owner of the list
        // 3) ROLE_USER and an editor of the list
        AlaUserProfile profile = getUserProfile(principal);

        return profile != null && (
        (list.getOwner() != null && list.getOwner().equals(profile.getUserId()))
        || (list.getEditors() != null && list.getEditors().contains(profile.getUserId()))
        || isAuthorized(principal)
        );
    }
}
