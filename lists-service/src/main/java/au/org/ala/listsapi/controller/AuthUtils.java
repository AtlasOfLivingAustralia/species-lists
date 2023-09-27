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

  @Value("#{'${security.admin.role}'.split(',')}:ROLE_ADMIN")
  private List<String> adminRoles;

  public boolean isAuthorized(Principal principal) {
    // Principal needs to on of the following:
    //  1) ROLE_ADMIN
    //  2) ROLE_USER and is the owner of the list
    //  3) ROLE_USER and an editor of the list
    AlaUserProfile alaUserProfile = null;

    if (principal instanceof PreAuthenticatedAuthenticationToken) {
      alaUserProfile =
          (AlaUserProfile) ((PreAuthenticatedAuthenticationToken) principal).getPrincipal();
    } else if (principal instanceof AlaUserProfile) {
      alaUserProfile = (AlaUserProfile) principal;
    } else {
      return false;
    }

    if (alaUserProfile == null) return false;
    return (alaUserProfile.getRoles() != null && hasAdminRole(alaUserProfile));
  }

  public boolean isAuthorized(SpeciesList list, Principal principal) {
    // Principal needs to on of the following:
    //  1) ROLE_ADMIN
    //  2) ROLE_USER and is the owner of the list
    //  3) ROLE_USER and an editor of the list
    AlaUserProfile alaUserProfile = null;

    if (principal instanceof PreAuthenticatedAuthenticationToken) {
      alaUserProfile =
          (AlaUserProfile) ((PreAuthenticatedAuthenticationToken) principal).getPrincipal();
    } else if (principal instanceof AlaUserProfile) {
      alaUserProfile = (AlaUserProfile) principal;
    } else {
      return false;
    }

    if (alaUserProfile == null) return false;
    return (list.getOwner() != null && list.getOwner().equals(alaUserProfile.getUserId()))
        || (list.getEditors() != null && list.getEditors().contains(alaUserProfile.getUserId()))
        || (alaUserProfile.getRoles() != null && hasAdminRole(alaUserProfile));
  }

  public boolean hasAdminRole(AlaUserProfile alaUserProfile) {
    if (alaUserProfile == null) return false;
    if (alaUserProfile.getRoles() == null) return false;
    if (adminRoles == null) return false;
    for (String role : alaUserProfile.getRoles()) {
      if (adminRoles.contains(role)) {
        return true;
      }
    }
    return false;
  }
}
