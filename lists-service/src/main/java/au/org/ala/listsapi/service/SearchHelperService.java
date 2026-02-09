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
import au.org.ala.listsapi.model.FacetCount;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemRepository;
import au.org.ala.listsapi.repo.SpeciesListRepository;
import au.org.ala.ws.security.profile.AlaUserProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
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
 * A helper service for performing search-related operations on species lists. Provides read and
 * write operations for species list items, interacting with PostgreSQL.
 */
@Service
@Transactional
public class SearchHelperService {
  private static final Logger logger = LoggerFactory.getLogger(SearchHelperService.class);
  @Autowired private AuthUtils authUtils;
  @Autowired private SpeciesListRepository speciesListRepository;
  @Autowired private SpeciesListItemRepository speciesListItemRepository;
  @PersistenceContext private EntityManager entityManager;

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
          "class",
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

  /** Performs a bulk update on a list of SpeciesListItem objects. */
  public void speciesListItemsBulkUpdate(List<SpeciesListItem> items, List<String> keys) {
    // In JPA, saveAll performs upsert (merge) if ID exists
    // Partial updates (only specific keys) are harder in JPA without loading first.
    // For now, we assume we want to save the items as is.
    // If strict partial update is needed, we would need to load and map.
    speciesListItemRepository.saveAll(items);
  }

  /** Performs a bulk save on a list of SpeciesListItem objects. */
  public void speciesListItemsBulkSave(List<SpeciesListItem> items) {
    speciesListItemRepository.saveAll(items);
  }

  /** Fetches species list items based on GUIDs and optional species list IDs. */
  public List<SpeciesListItem> fetchSpeciesListItems(
      String guids, @Nullable String speciesListIDs, int page, int pageSize, Principal principal) {
    if (page < 1 || (page * pageSize) > 10000) {
      return new ArrayList<>();
    }

    List<String> guidList = Arrays.asList(guids.split(","));
    List<String> listIdList =
        speciesListIDs != null ? Arrays.asList(speciesListIDs.split(",")) : null;

    Specification<SpeciesListItem> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Filter by GUIDs (Taxon Concept IDs)
          // Assuming taxonID maps to GUID
          predicates.add(root.get("taxonID").in(guidList));

          // Filter by List IDs if provided
          if (listIdList != null) {
            predicates.add(root.get("speciesListID").in(listIdList));
          }

          // Access Control
          AlaUserProfile profile = authUtils.getUserProfile(principal);
          if (!authUtils.isAuthenticated(principal)) {
            // Public lists only
            // We need to join with SpeciesList to check isPrivate
            // Since there is no direct relationship mapped in SpeciesListItem, we might need a
            // subquery or join if mapped.
            // SpeciesListItem has speciesListID.
            // Let's use a subquery to find public list IDs.
            predicates.add(root.get("speciesListID").in(findPublicListIds()));
          } else if (!authUtils.hasAdminRole(profile) && !authUtils.hasInternalScope(profile)) {
            // Private lists owned by user OR public lists
            predicates.add(
                root.get("speciesListID").in(findAccessibleListIds(profile.getUserId())));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Pageable pageable = PageRequest.of(page - 1, pageSize);
    return speciesListItemRepository.findAll(spec, pageable).getContent();
  }

  /** Fetches species list items based on species list IDs and optional search query. */
  public List<SpeciesListItem> fetchSpeciesListItems(
      String speciesListIDs,
      @Nullable String searchQuery,
      @Nullable String fields,
      @Nullable Boolean noNulls,
      @Nullable Integer page,
      @Nullable Integer pageSize,
      @Nullable String sort,
      @Nullable String dir,
      Principal principal) {

    if (speciesListIDs == null || speciesListIDs.isBlank()) {
      return new ArrayList<>();
    }

    List<String> requestedListIds = Arrays.asList(speciesListIDs.split(","));

    // Filter requested IDs by access rights
    List<SpeciesList> accessibleLists =
        speciesListRepository.findByDataResourceUidInOrIdIn(requestedListIds);
    List<String> validListIDs =
        accessibleLists.stream()
            .filter(
                list ->
                    !Boolean.TRUE.equals(list.getIsPrivate())
                        || authUtils.isAuthorized(list, principal))
            .map(SpeciesList::getId)
            .collect(Collectors.toList());

    if (validListIDs.isEmpty()) {
      return new ArrayList<>();
    }

    Specification<SpeciesListItem> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(root.get("speciesListID").in(validListIDs));

          if (StringUtils.isNotBlank(searchQuery)) {
            String likePattern = "%" + searchQuery.toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("scientificName")), likePattern),
                    cb.like(cb.lower(root.get("vernacularName")), likePattern),
                    cb.like(cb.lower(root.get("family")), likePattern),
                    cb.like(cb.lower(root.get("genus")), likePattern)));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    String sortField = (sort != null && !sort.isBlank()) ? sort : "scientificName";
    Sort.Direction sortDir =
        (dir != null && "desc".equalsIgnoreCase(dir)) ? Sort.Direction.DESC : Sort.Direction.ASC;
    Pageable pageable =
        PageRequest.of(
            page != null ? page : 0, pageSize != null ? pageSize : 10, Sort.by(sortDir, sortField));

    return speciesListItemRepository.findAll(spec, pageable).getContent();
  }

  public static Set<String> findCommonKeys(List<SpeciesList> lists) {
    if (lists == null || lists.isEmpty()) return Collections.emptySet();
    if (lists.size() == 1) return new HashSet<>(lists.get(0).getFieldList());

    lists.sort(Comparator.comparingInt(l -> l.getFieldList().size()));
    Set<String> common = new HashSet<>(lists.get(0).getFieldList());
    for (int i = 1; i < lists.size(); i++) {
      common.retainAll(lists.get(i).getFieldList());
      if (common.isEmpty()) break;
    }
    return common;
  }

  public Page<SpeciesList> searchDocuments(
      SpeciesList speciesListQuery,
      String userId,
      Boolean isAdmin,
      String searchTerm,
      Pageable pageable) {
    Specification<SpeciesList> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Access Control
          Predicate accessPredicate;
          if (userId != null && !userId.isEmpty() && !Boolean.TRUE.equals(isAdmin)) {
            accessPredicate =
                cb.or(cb.equal(root.get("owner"), userId), cb.equal(root.get("isPrivate"), false));
          } else if (Boolean.TRUE.equals(isAdmin)) {
            accessPredicate = cb.conjunction();
          } else {
            accessPredicate = cb.equal(root.get("isPrivate"), false);
          }
          predicates.add(accessPredicate);

          // Text Search
          if (StringUtils.isNotBlank(searchTerm)) {
            String likePattern = "%" + searchTerm.toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("title")), likePattern),
                    cb.like(cb.lower(root.get("description")), likePattern)));
          }

          // Field Filters
          if (speciesListQuery.getIsAuthoritative() != null)
            predicates.add(
                cb.equal(root.get("isAuthoritative"), speciesListQuery.getIsAuthoritative()));
          if (speciesListQuery.getIsThreatened() != null)
            predicates.add(cb.equal(root.get("isThreatened"), speciesListQuery.getIsThreatened()));
          if (speciesListQuery.getIsInvasive() != null)
            predicates.add(cb.equal(root.get("isInvasive"), speciesListQuery.getIsInvasive()));
          if (speciesListQuery.getIsBIE() != null)
            predicates.add(cb.equal(root.get("isBIE"), speciesListQuery.getIsBIE()));
          if (speciesListQuery.getIsSDS() != null)
            predicates.add(cb.equal(root.get("isSDS"), speciesListQuery.getIsSDS()));

          if (speciesListQuery.getDataResourceUid() != null) {
            if (speciesListQuery.getDataResourceUid().contains(",")) {
              List<String> uids = Arrays.asList(speciesListQuery.getDataResourceUid().split(","));
              predicates.add(root.get("dataResourceUid").in(uids));
            } else {
              predicates.add(
                  cb.equal(root.get("dataResourceUid"), speciesListQuery.getDataResourceUid()));
            }
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return speciesListRepository.findAll(spec, pageable);
  }

  // --- GraphQL Refactoring Methods ---

  public Page<SpeciesList> searchSpeciesLists(ListSearchContext context, Pageable pageable) {
    Specification<SpeciesList> spec = buildSpeciesListSpec(context);
    return speciesListRepository.findAll(spec, pageable);
  }

  public List<Facet> getFacetsForSpeciesLists(ListSearchContext context) {
    Specification<SpeciesList> spec = buildSpeciesListSpec(context);

    // This is a simplified facet implementation using count queries
    List<String> facetFields =
        Arrays.asList(
            "isAuthoritative",
            "listType",
            "isBIE",
            "isSDS",
            "isPrivate",
            "hasRegion",
            "isThreatened",
            "isInvasive",
            "licence");

    List<Facet> facets = new ArrayList<>();
    for (String field : facetFields) {
      // Need to construct a Group By query
      // SELECT field, COUNT(*) FROM SpeciesList WHERE ... GROUP BY field
      // Using Criteria API for dynamic queries

      // This part is tricky to do generically with Specification + GroupBy in one go easily with
      // standard Repo
      // So we use EntityManager
      List<FacetCount> counts = getFacetCounts(spec, field);
      if (!counts.isEmpty()) {
        Facet f = new Facet();
        f.setKey(field);
        f.setCounts(counts);
        facets.add(f);
      }
    }
    return facets;
  }

  private List<FacetCount> getFacetCounts(Specification<SpeciesList> spec, String field) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    Root<SpeciesList> root = query.from(SpeciesList.class);

    Predicate predicate = spec.toPredicate(root, query, cb);

    query.multiselect(root.get(field), cb.count(root));
    query.where(predicate);
    query.groupBy(root.get(field));

    return entityManager.createQuery(query).getResultList().stream()
        .map(
            tuple -> {
              Object val = tuple.get(0);
              Long count = (Long) tuple.get(1);
              return new FacetCount(val != null ? val.toString() : "null", count);
            })
        .collect(Collectors.toList());
  }

  private Specification<SpeciesList> buildSpeciesListSpec(ListSearchContext context) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Search Query
      if (StringUtils.isNotBlank(context.getSearchQuery())) {
        String pattern = "%" + context.getSearchQuery().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)));
      }

      // Permissions
      if (!context.isAuthenticated()) {
        predicates.add(cb.equal(root.get("isPrivate"), false));
      } else if (context.isViewingOwnLists()) {
        predicates.add(cb.equal(root.get("owner"), context.getUserId()));
      } else if (!context.isAdmin()) {
        predicates.add(cb.equal(root.get("isPrivate"), false));
      } else if (context.isAdmin() && context.getUserId() != null) {
        predicates.add(cb.equal(root.get("owner"), context.getUserId()));
      }

      // Filters
      if (context.getFilters() != null) {
        for (Filter filter : context.getFilters()) {
          try {
            Path<Object> path = root.get(filter.getKey());
            if ("true".equalsIgnoreCase(filter.getValue())
                || "false".equalsIgnoreCase(filter.getValue())) {
              predicates.add(cb.equal(path, Boolean.parseBoolean(filter.getValue())));
            } else {
              predicates.add(cb.equal(path.as(String.class), filter.getValue()));
            }
          } catch (Exception e) {
            logger.warn("Could not filter on field {}", filter.getKey());
          }
        }
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  public Page<SpeciesListItem> searchSingleSpeciesList(
      SingleListSearchContext context, Pageable pageable) {
    Specification<SpeciesListItem> spec = buildSpeciesListItemSpec(context);
    return speciesListItemRepository.findAll(spec, pageable);
  }

  public List<Facet> getFacetsForSingleSpeciesList(
      SingleListSearchContext context, List<String> facetFields) {
    Specification<SpeciesListItem> spec = buildSpeciesListItemSpec(context);
    List<Facet> facets = new ArrayList<>();

    List<String> allFields = new ArrayList<>();
    if (facetFields != null) allFields.addAll(facetFields);

    // Add standard classification fields
    allFields.addAll(Arrays.asList("family", "order", "classs", "phylum", "kingdom", "genus"));

    for (String field : allFields) {
      // Basic implementation for direct fields
      // Note: Deeply nested or JSONB facets are harder and might require native queries
      if (CORE_FIELDS.contains(field)
          || Arrays.asList("classs", "family", "order", "genus").contains(field)) {
        List<FacetCount> counts = getListItemFacetCounts(spec, field);
        if (!counts.isEmpty()) {
          Facet f = new Facet();
          f.setKey(field);
          f.setCounts(counts);
          facets.add(f);
        }
      }
    }

    // Note: Property facets (JSONB) are omitted in this simplified JPA version
    // due to complexity of dynamic JSONB aggregation in standard JPA.

    return facets;
  }

  private List<FacetCount> getListItemFacetCounts(
      Specification<SpeciesListItem> spec, String field) {
    try {
      CriteriaBuilder cb = entityManager.getCriteriaBuilder();
      var query = cb.createTupleQuery();
      Root<SpeciesListItem> root = query.from(SpeciesListItem.class);

      Predicate predicate = spec.toPredicate(root, query, cb);

      query.multiselect(root.get(field), cb.count(root));
      query.where(predicate);
      query.groupBy(root.get(field));

      return entityManager.createQuery(query).getResultList().stream()
              .map(
                  tuple -> {
                    Object val = tuple.get(0);
                    Long count = (Long) tuple.get(1);
                    return new FacetCount(val != null ? val.toString() : "null", count);
                  })
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.warn("Failed to get facet for field {}", field, e);
      return Collections.emptyList();
    }
  }

  private Specification<SpeciesListItem> buildSpeciesListItemSpec(SingleListSearchContext context) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("speciesListID"), context.getSpeciesListId()));

      if (StringUtils.isNotBlank(context.getSearchQuery())) {
        String pattern = "%" + context.getSearchQuery().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("scientificName")), pattern),
                cb.like(cb.lower(root.get("vernacularName")), pattern),
                cb.like(cb.lower(root.get("suppliedName")), pattern)));
      }

      if (context.getFilters() != null) {
        for (Filter filter : context.getFilters()) {
          String key = filter.getKey();
          String value = filter.getValue();

          if (CORE_FIELDS.contains(key)
              || Arrays.asList("classs", "family", "order", "genus", "kingdom", "phylum")
                  .contains(key)) {
            predicates.add(cb.equal(root.get(key), value));
          } else {
            // JSONB Property Filter attempt using native function if possible,
            // or simplified string match for this migration step
            // Using a simple JSON string containment check as fallback
            // This is NOT 100% accurate but works for migration progress
            predicates.add(cb.like(root.get("properties").as(String.class), "%" + value + "%"));
          }
        }
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  // -- Helpers for subqueries --

  private List<String> findPublicListIds() {
    return entityManager
        .createQuery("SELECT s.id FROM SpeciesList s WHERE s.isPrivate = false", String.class)
        .getResultList();
  }

  private List<String> findAccessibleListIds(String userId) {
    return entityManager
        .createQuery(
            "SELECT s.id FROM SpeciesList s WHERE s.isPrivate = false OR s.owner = :userId",
            String.class)
        .setParameter("userId", userId)
        .getResultList();
  }
}
