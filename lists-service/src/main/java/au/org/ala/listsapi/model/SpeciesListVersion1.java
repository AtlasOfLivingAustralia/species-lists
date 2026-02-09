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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;
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
public class SpeciesListVersion1 implements Serializable {

  private static final long serialVersionUID = 1L;
  @Id @JsonIgnore private String id;
  private String dataResourceUid;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
  private Date dateCreated;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
  private Date lastUpdated;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
  private Date lastUploaded;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
  private Date lastMatched;

  private String listName;
  private String description;
  private String listType;
  private String category;
  private String username;
  private String fullName;
  private String authority;
  private String region;
  private String sdsType;
  private String generalisation;
  private String wkt;

  private Integer itemCount;

  private Boolean isAuthoritative;
  private Boolean isPrivate;
  private Boolean isInvasive;
  private Boolean isThreatened;
  private Boolean isSDS;
  private Boolean isBIE;
  private Boolean looseSearch;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDataResourceUid() {
    return dataResourceUid;
  }

  public void setDataResourceUid(String dataResourceUid) {
    this.dataResourceUid = dataResourceUid;
  }

  public Date getDateCreated() {
    return dateCreated;
  }

  public void setDateCreated(Date dateCreated) {
    this.dateCreated = dateCreated;
  }

  public Date getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public Date getLastUploaded() {
    return lastUploaded;
  }

  public void setLastUploaded(Date lastUploaded) {
    this.lastUploaded = lastUploaded;
  }

  public Date getLastMatched() {
    return lastMatched;
  }

  public void setLastMatched(Date lastMatched) {
    this.lastMatched = lastMatched;
  }

  public String getListName() {
    return listName;
  }

  public void setListName(String listName) {
    this.listName = listName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getListType() {
    return listType;
  }

  public void setListType(String listType) {
    this.listType = listType;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getSdsType() {
    return sdsType;
  }

  public void setSdsType(String sdsType) {
    this.sdsType = sdsType;
  }

  public String getGeneralisation() {
    return generalisation;
  }

  public void setGeneralisation(String generalisation) {
    this.generalisation = generalisation;
  }

  public String getWkt() {
    return wkt;
  }

  public void setWkt(String wkt) {
    this.wkt = wkt;
  }

  public Integer getItemCount() {
    return itemCount;
  }

  public void setItemCount(Integer itemCount) {
    this.itemCount = itemCount;
  }

  public Boolean getIsAuthoritative() {
    return isAuthoritative;
  }

  public void setIsAuthoritative(Boolean isAuthoritative) {
    this.isAuthoritative = isAuthoritative;
  }

  public Boolean getIsPrivate() {
    return isPrivate;
  }

  public void setIsPrivate(Boolean isPrivate) {
    this.isPrivate = isPrivate;
  }

  public Boolean getIsInvasive() {
    return isInvasive;
  }

  public void setIsInvasive(Boolean isInvasive) {
    this.isInvasive = isInvasive;
  }

  public Boolean getIsThreatened() {
    return isThreatened;
  }

  public void setIsThreatened(Boolean isThreatened) {
    this.isThreatened = isThreatened;
  }

  public Boolean getIsSDS() {
    return isSDS;
  }

  public void setIsSDS(Boolean isSDS) {
    this.isSDS = isSDS;
  }

  public Boolean getIsBIE() {
    return isBIE;
  }

  public void setIsBIE(Boolean isBIE) {
    this.isBIE = isBIE;
  }

  public Boolean getLooseSearch() {
    return looseSearch;
  }

  public void setLooseSearch(Boolean looseSearch) {
    this.looseSearch = looseSearch;
  }
}
