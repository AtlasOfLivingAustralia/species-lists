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
import java.util.Date;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing a species list version for /v1 backwards compatibility.
 * This POJO is used for controller response serialization only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeciesListVersion1 implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @JsonIgnore
    private String id;
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
}