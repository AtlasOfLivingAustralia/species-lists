package au.org.ala.listsapi.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Context object for single list searches
 */
@Data
@Builder
public  class SingleListSearchContext {
    private String speciesListId;
    private SpeciesList speciesList;
    private String searchQuery;
    private List<Filter> filters;
    private String userId;
    private String sort;
    private String dir;
    private boolean isAdmin;
}