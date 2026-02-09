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

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

/**
 * Model class representing a species list version for /v1 backwards compatibility. This POJO is
 * used for controller response serialization only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeciesListItemVersion1 implements Serializable {

  private static final long serialVersionUID = 1L;
  @Id private Long id;
  private String dataResourceUid;
  private String lsid;
  private String name;
  private String scientificName;
  private String commonName;
  private List<KvpValueVersion1> kvpValues;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getDataResourceUid() {
    return dataResourceUid;
  }

  public void setDataResourceUid(String dataResourceUid) {
    this.dataResourceUid = dataResourceUid;
  }

  public String getLsid() {
    return lsid;
  }

  public void setLsid(String lsid) {
    this.lsid = lsid;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  public String getCommonName() {
    return commonName;
  }

  public void setCommonName(String commonName) {
    this.commonName = commonName;
  }

  public List<KvpValueVersion1> getKvpValues() {
    return kvpValues;
  }

  public void setKvpValues(List<KvpValueVersion1> kvpValues) {
    this.kvpValues = kvpValues;
  }
}
