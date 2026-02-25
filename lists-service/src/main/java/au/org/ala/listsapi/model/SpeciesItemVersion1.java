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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Model class representing a single species list item for /v1 backwards compatibility. This POJO
 * is used for controller response serialization only (e.g. for /v1/species/** endpoints).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeciesItemVersion1 implements Serializable {

  private static final long serialVersionUID = 1L;
  @JsonIgnore
  @Id private Long id;
  private String dataResourceUid;
  private String guid;
  private AbbrListVersion1 list;
  private List<KvpValueVersion1> kvpValues;
}
