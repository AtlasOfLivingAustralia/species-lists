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
 * Model class representing a list kvp value for /v1 backwards compatibility.
 * This POJO is used for controller response serialization only.
 */
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
public class KvpValueVersion1 {
    private String key;
    private String value;
    private String vocabValue; // not used in new lists but keeping for backwards compatibility

    public KvpValueVersion1(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
