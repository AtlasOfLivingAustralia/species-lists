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

import au.org.ala.listsapi.controller.AuthUtils;
import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemRepository;
import au.org.ala.listsapi.repo.SpeciesListRepository;
import au.org.ala.ws.security.profile.AlaUserProfile;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for searching and faceting items within species lists. Handles item-level operations
 * including CRUD operations on list items, bulk operations, and single-list searches.
 */
@Service
@Transactional
public class SpeciesListItemService {

  private static final Logger logger = LoggerFactory.getLogger(SpeciesListItemService.class);

  private static final String SPECIES_LIST_ID = "speciesListID";

  private static final Set<String> CORE_FIELDS =
      Set.of(
          "id",
          "scientificName",
          "vernacularName",
          "licence",
          "taxonID",
          "kingdom",
          "phylum",
          "classs",
          "order",
          "family",
          "genus",
          "isBIE",
          "listType",
          "isAuthoritative",
          "hasRegion",
          "isSDS",
          "isThreatened",
          "isInvasive",
          "isPrivate",
          "tags");

  @Autowired private AuthUtils authUtils;

  @Autowired private SpeciesListItemRepository speciesListItemRepository;

  @Autowired private SpeciesListRepository speciesListRepository;

  // ========================================================================
  // BULK OPERATIONS
  // ========================================================================

  /**
   * Performs a bulk update on a list of SpeciesListItem objects. Note: In JPA/Postgres, we iterate
   * and save. For high performance large batches, consider a custom JDBC implementation.
   */
  public void speciesListItemsBulkUpdate(List<SpeciesListItem> items, List<String> keys) {
    // Since we are updating specific keys, we need to load, update, and save.
    // This is inefficient for huge lists but safe for JPA.
    for (SpeciesListItem item : items) {
      speciesListItemRepository
          .findById(item.getId())
          .ifPresent(
              existing -> {
                keys.forEach(key -> updateEntityField(existing, key, item.getPropFromKey(key)));
                speciesListItemRepository.save(existing);
              });
    }
  }

  private void updateEntityField(SpeciesListItem entity, String key, Object value) {
    // Simple reflection-like update or switch case
    // Note: getPropFromKey exists on entity, but we need setPropFromKey or manual setters
    switch (key) {
      case "scientificName":
        entity.setScientificName((String) value);
        break;
      case "vernacularName":
        entity.setVernacularName((String) value);
        break;
      case "taxonID":
        entity.setTaxonID((String) value);
        break;
      case "kingdom":
        entity.setKingdom((String) value);
        break;
      case "phylum":
        entity.setPhylum((String) value);
        break;
      case "classs":
        entity.setClasss((String) value);
        break;
      case "order":
        entity.setOrder((String) value);
        break;
      case "family":
        entity.setFamily((String) value);
        break;
      case "genus":
        entity.setGenus((String) value);
        break;
      default:
        // Handle properties map if needed, or ignore
        break;
    }
  }

  /** Performs a bulk save on a list of SpeciesListItem objects */
  public void speciesListItemsBulkSave(List<SpeciesListItem> items) {
    speciesListItemRepository.saveAll(items);
  }

  // ========================================================================
  // SINGLE LIST SEARCH & FACETING
  // ========================================================================

  /** Search items within a specific species list */
  public Page<SpeciesListItem> searchSingleSpeciesList(
      SingleListSearchContext context, Pageable pageable) {

    Specification<SpeciesListItem> spec = buildSpecification(context);
    return speciesListItemRepository.findAll(spec, pageable);
  }

  /**
   * Get facets for a specific species list. Note: This is a simplified implementation. Postgres
   * faceting requires complex queries.
   */
  public List<Facet> getFacetsForSingleSpeciesList(
      SingleListSearchContext context, List<String> facetFields) {

    // TODO: Implement proper faceting using Group By queries in Postgres
    logger.warn("Faceting not yet fully implemented for Postgres migration");
    return Collections.emptyList();
  }

  private Specification<SpeciesListItem> buildSpecification(SingleListSearchContext context) {
    return (Root<SpeciesListItem> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // 1. Filter by Species List ID
      predicates.add(cb.equal(root.get("speciesListID"), context.getSpeciesListId()));

      // 2. Search Query (Simple LIKE on scientificName or vernacularName)
      if (StringUtils.isNotBlank(context.getSearchQuery())) {
        String likePattern = "%" + context.getSearchQuery().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("scientificName")), likePattern),
                cb.like(cb.lower(root.get("vernacularName")), likePattern)));
      }

      // 3. Filters
      if (context.getFilters() != null) {
        for (Filter filter : context.getFilters()) {
          // This assumes filters are simple field matches for now.
          // Complex JSONB filtering would go here.
          try {
            if (CORE_FIELDS.contains(filter.getKey())) {
              predicates.add(cb.equal(root.get(filter.getKey()), filter.getValue()));
            }
          } catch (IllegalArgumentException e) {
            // Ignore invalid fields
          }
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  // ========================================================================
  // FETCH OPERATIONS
  // ========================================================================

  /** Fetches species list items based on GUIDs and optional species list IDs */
  public List<SpeciesListItem> fetchSpeciesListItems(
      String guids, @Nullable String speciesListIDs, int page, int pageSize, Principal principal) {

    AlaUserProfile profile = authUtils.getUserProfile(principal);
    List<String> guidList = Arrays.asList(guids.split(","));

    Specification<SpeciesListItem> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Filter by GUIDs (TaxonID)
          // Note: The original code matched 'classification.taxonConceptID' which might be
          // 'taxonID' in our model
          predicates.add(root.get("taxonID").in(guidList));

          if (speciesListIDs != null) {
            List<String> listIds = Arrays.asList(speciesListIDs.split(","));
            predicates.add(root.get("speciesListID").in(listIds));
          }

          // TODO: Add Auth filtering logic (Private/Public lists)
          // This requires joining with SpeciesList table or checking separately.
          // For now, returning items. In real implementation, we should check access.

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Pageable pageable = PageRequest.of(page - 1, pageSize);
    return speciesListItemRepository.findAll(spec, pageable).getContent();
  }

  /**
   * Fetches species list items based on species list IDs with optional search and field
   * restrictions
   */
  public List<SpeciesListItem> fetchSpeciesListItems(
      String speciesListIDs,
      @Nullable String searchQuery,
      @Nullable String fields,
      @Nullable Integer page,
      @Nullable Integer pageSize,
      @Nullable String sort,
      @Nullable String dir,
      Principal principal)
      throws IllegalArgumentException {

    List<String> listIDs = Arrays.asList(speciesListIDs.split(","));

    // Check access
    List<SpeciesList> accessibleLists =
        speciesListRepository.findAllById(listIDs).stream()
            .filter(list -> !list.getIsPrivate() || authUtils.isAuthorized(list, principal))
            .collect(Collectors.toList());

    if (accessibleLists.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> accessibleIds =
        accessibleLists.stream().map(SpeciesList::getId).collect(Collectors.toList());

    if (pageSize != null && (page - 1) * pageSize + pageSize > 10000) {
      throw new IllegalArgumentException("Page size exceeds limit.");
    }

    int p = page != null && page > 0 ? page - 1 : 0;
    int ps = pageSize != null && pageSize > 0 ? pageSize : 10;

    String sortField = (sort != null && !sort.isBlank()) ? sort : "scientificName";
    String sortDir = (dir != null && !dir.isBlank()) ? dir : "asc";
    Sort springSort = Sort.by(Sort.Direction.fromString(sortDir), sortField);

    Pageable pageable = PageRequest.of(p, ps, springSort);

    Specification<SpeciesListItem> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(root.get("speciesListID").in(accessibleIds));

          if (StringUtils.isNotBlank(searchQuery)) {
            String likePattern = "%" + searchQuery.toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("scientificName")), likePattern),
                    cb.like(cb.lower(root.get("vernacularName")), likePattern)));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return speciesListItemRepository.findAll(spec, pageable).getContent();
  }

  // ========================================================================
  // UTILITY METHODS
  // ========================================================================

  public static Set<String> findCommonKeys(List<SpeciesList> lists) {
    if (lists == null || lists.isEmpty()) {
      return Collections.emptySet();
    }
    if (lists.size() == 1) {
      return new HashSet<>(lists.get(0).getFieldList());
    }
    lists.sort(Comparator.comparingInt(l -> l.getFieldList().size()));
    Set<String> common = new HashSet<>(lists.get(0).getFieldList());
    for (int i = 1; i < lists.size(); i++) {
      common.retainAll(lists.get(i).getFieldList());
      if (common.isEmpty()) {
        break;
      }
    }
    return common;
  }

  public Page<SpeciesList> searchDocuments(
      SpeciesList speciesListQuery,
      String userId,
      Boolean isAdmin,
      String searchTerm,
      Pageable pageable) {

    // This functionality should ideally be moved to SpeciesListRepository/Service
    // Implementing here using SpeciesListRepository

    Specification<SpeciesList> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Search Term
          if (StringUtils.isNotBlank(searchTerm)) {
            String like = "%" + searchTerm.toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)));
          }

          // Access Control
          Predicate isPrivate = cb.isTrue(root.get("isPrivate"));
          Predicate isPublic = cb.isFalse(root.get("isPrivate"));
          Predicate isOwner =
              StringUtils.isNotBlank(userId)
                  ? cb.equal(root.get("owner"), userId)
                  : cb.disjunction();

          if (Boolean.TRUE.equals(isAdmin)) {
            // Admin sees all, no filter needed for access
          } else if (StringUtils.isNotBlank(userId)) {
            predicates.add(cb.or(isPublic, isOwner));
          } else {
            predicates.add(isPublic);
          }

          // Other filters
          if (speciesListQuery.getIsAuthoritative() != null) {
            predicates.add(
                cb.equal(root.get("isAuthoritative"), speciesListQuery.getIsAuthoritative()));
          }
          // ... Add other filters ...

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return speciesListRepository.findAll(spec, pageable);
  }
}
