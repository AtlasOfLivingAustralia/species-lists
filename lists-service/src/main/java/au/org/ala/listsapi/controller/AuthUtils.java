package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.ws.security.profile.AlaUserProfile;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {

  @Value("#{'${security.admin.role}'.split(',')}")
  private List<String> adminRoles;

  AlaUserProfile getUserProfile(Principal principal) {
    AlaUserProfile profile = null;

    if (principal instanceof PreAuthenticatedAuthenticationToken) {
      profile = (AlaUserProfile) ((PreAuthenticatedAuthenticationToken) principal).getPrincipal();
    } else if (principal instanceof AlaUserProfile) {
      profile = (AlaUserProfile) principal;
    }

    return profile;
  }

  boolean hasAdminRole(AlaUserProfile profile) {
    if (adminRoles == null)
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
