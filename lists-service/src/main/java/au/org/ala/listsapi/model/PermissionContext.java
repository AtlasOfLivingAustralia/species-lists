package au.org.ala.listsapi.model;

import lombok.Builder;
import lombok.Data;

/**
 * Permission context for access control (Controller internal use)
 */
@Data
@Builder
public class PermissionContext {
    private boolean isAuthenticated;
    private boolean isAdmin;
    private String currentUserId;
    private String effectiveUserId;
    private boolean isViewingOwnLists;
}
