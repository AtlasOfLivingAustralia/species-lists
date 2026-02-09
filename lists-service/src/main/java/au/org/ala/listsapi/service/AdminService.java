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
package au.org.ala.listsapi.service;

import au.org.ala.listsapi.repo.SpeciesListItemRepository;
import au.org.ala.listsapi.repo.SpeciesListRepository;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
  @Autowired protected SpeciesListRepository speciesListRepository;
  @Autowired protected SpeciesListItemRepository speciesListItemRepository;

  public void deleteDocs() {
    speciesListItemRepository.deleteAll();
    speciesListRepository.deleteAll();
  }

  public void deleteIndex() {
    // No-op for Postgres migration
  }

  public Map<String, Object> getMongoIndexes() {
    return Collections.emptyMap();
  }
}
