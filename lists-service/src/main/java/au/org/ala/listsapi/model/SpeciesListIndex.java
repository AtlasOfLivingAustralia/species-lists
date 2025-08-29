/**
 * Copyright (c) 2025 Atlas of Living Australia
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

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * SpeciesListIndex is a model/bean that represents a single elastic search document and
 * each entry corresponds to a denormalised taxon row entry for a species list. Individual 
 * lists are represented by aggregating the entries for each list's taxa in the index.
 * Note: any changes to this file will require the ElasticSearch index to be deleted, 
 * recreated and reindexed again. Otherwise the mappings.json file is not sent to the server.
 */
@Document(indexName = "species-lists", createIndex = true)
@Setting(settingPath = "/elasticsearch/settings.json")
@Mapping(mappingPath = "/elasticsearch/mappings.json")
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class SpeciesListIndex {
    @Id private String id;
    private String dataResourceUid;
    private String speciesListName;
    private String listType;
    private String speciesListID;
    private String suppliedName;
    private String scientificName;
    private String vernacularName;
    private String taxonID;
    private String kingdom;
    private String phylum;
    private String classs;
    private String order;
    private String family;
    private String genus;
    private List<KeyValue> properties;
    private Classification classification;
    private boolean isPrivate;
    private boolean isAuthoritative;
    private boolean isBIE;
    private boolean isSDS;
    private boolean isThreatened;
    private boolean isInvasive;
    private boolean hasRegion;
    private String owner;
    private List<String> editors;
    private List<String> tags;
    public String dateCreated;
    public String lastUpdated;
    private String lastUpdatedBy;

    @JsonProperty("class")
    public String getClasss() {
        return classs;
    }
}
