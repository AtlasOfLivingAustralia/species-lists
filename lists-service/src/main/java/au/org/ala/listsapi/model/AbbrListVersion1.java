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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Model class representing a list item for /v1 backwards compatibility. This POJO is used for
 * controller response serialization only.
 */
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class AbbrListVersion1 {
  private String username;
  private String listName;
  private Boolean sds;
  private Boolean isBIE;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getListName() {
    return listName;
  }

  public void setListName(String listName) {
    this.listName = listName;
  }

  public Boolean getSds() {
    return sds;
  }

  public void setSds(Boolean sds) {
    this.sds = sds;
  }

  public Boolean getIsBIE() {
    return isBIE;
  }

  public void setIsBIE(Boolean isBIE) {
    this.isBIE = isBIE;
  }
}
