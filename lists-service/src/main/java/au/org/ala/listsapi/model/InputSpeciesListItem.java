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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class InputSpeciesListItem {
    private String id;
    private String speciesListID;
    private String taxonID;
    private String scientificName;
    private String vernacularName;
    private String kingdom;
    private String phylum;
    private String classs;
    private String order;
    private String family;
    private String genus;
    private List<InputKeyValue> properties;

    public Map<String, String> toTaxonMap() {
        Map<String, String> taxon = new HashMap<>();
        taxon.put("taxonID", this.taxonID);
        taxon.put("scientificName", this.scientificName);
        taxon.put("vernacularName", this.vernacularName);
        taxon.put("kingdom", this.kingdom);
        taxon.put("phylum", this.phylum);
        taxon.put("class", this.classs);
        taxon.put("order", this.order);
        taxon.put("family", this.family);
        taxon.put("genus", this.genus);
        return taxon;
    }
}
