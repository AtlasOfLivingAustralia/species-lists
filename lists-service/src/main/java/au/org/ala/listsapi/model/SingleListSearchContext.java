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
package au.org.ala.listsapi.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Context object for single list searches */
@Data
@Builder
public class SingleListSearchContext {
  private String speciesListId;
  private SpeciesList speciesList;
  private String searchQuery;
  private List<Filter> filters;
  private String userId;
  private String sort;
  private String dir;
  private boolean isAdmin;

  public String getSpeciesListId() {
    return speciesListId;
  }

  public void setSpeciesListId(String speciesListId) {
    this.speciesListId = speciesListId;
  }

  public SpeciesList getSpeciesList() {
    return speciesList;
  }

  public void setSpeciesList(SpeciesList speciesList) {
    this.speciesList = speciesList;
  }

  public String getSearchQuery() {
    return searchQuery;
  }

  public void setSearchQuery(String searchQuery) {
    this.searchQuery = searchQuery;
  }

  public List<Filter> getFilters() {
    return filters;
  }

  public void setFilters(List<Filter> filters) {
    this.filters = filters;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getSort() {
    return sort;
  }

  public void setSort(String sort) {
    this.sort = sort;
  }

  public String getDir() {
    return dir;
  }

  public void setDir(String dir) {
    this.dir = dir;
  }

  public boolean isAdmin() {
    return isAdmin;
  }

  public void setAdmin(boolean admin) {
    isAdmin = admin;
  }
}
