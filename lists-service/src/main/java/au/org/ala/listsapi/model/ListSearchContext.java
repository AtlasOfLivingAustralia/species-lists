package au.org.ala.listsapi.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Context object for list searches
 */
@Data
@Builder
public class ListSearchContext {
    private String searchQuery;
    private List<Filter> filters;
    private String userId;
    private String sort;
    private String dir;
    private boolean isAdmin;
    private boolean isAuthenticated;
    private boolean isViewingOwnLists;
}