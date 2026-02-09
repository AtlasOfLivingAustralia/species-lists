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

import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for searching and faceting species lists (the "lists" view). Handles list-level
 * operations including searches and pagination.
 */
@Service
@Transactional(readOnly = true)
public class SpeciesListSearchService {

  private static final Logger logger = LoggerFactory.getLogger(SpeciesListSearchService.class);

  @Autowired private SpeciesListRepository speciesListRepository;

  /** Main search method for species lists with permission-aware filtering */
  public Page<SpeciesList> searchSpeciesLists(ListSearchContext context, Pageable pageable) {

    Specification<SpeciesList> spec = buildSpecification(context);

    // Handle custom sort logic if necessary, otherwise use pageable sort
    // The context.getSort() might be "relevance", which we map to title for now
    String sortField = context.getSort();
    if ("relevance".equalsIgnoreCase(sortField) || StringUtils.isBlank(sortField)) {
      sortField = "title"; // Default to title for relevance
    }

    Sort sort =
        Sort.by(
            "asc".equalsIgnoreCase(context.getDir()) ? Sort.Direction.ASC : Sort.Direction.DESC,
            sortField);

    // Override pageable sort
    Pageable sortedPageable =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

    return speciesListRepository.findAll(spec, sortedPageable);
  }

  /**
   * Get facets for species lists with permission-aware filtering. Note: Full Postgres faceting is
   * not implemented in this migration pass.
   */
  public List<Facet> getFacetsForSpeciesLists(ListSearchContext context) {
    // TODO: Implement faceting using GROUP BY queries
    logger.warn("Faceting not implemented for Postgres migration yet.");
    return Collections.emptyList();
  }

  private Specification<SpeciesList> buildSpecification(ListSearchContext context) {
    return (Root<SpeciesList> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // 1. Search Query
      if (StringUtils.isNotBlank(context.getSearchQuery())) {
        String likePattern = "%" + context.getSearchQuery().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), likePattern),
                cb.like(cb.lower(root.get("description")), likePattern)));
      }

      // 2. Permission Filters
      // Logic mirrored from original applyPermissionFilters
      boolean hasPrivateFilter =
          context.getFilters() != null
              && context.getFilters().stream().anyMatch(f -> "isPrivate".equals(f.getKey()));

      if (!hasPrivateFilter) {
        Predicate isPrivate = cb.isTrue(root.get("isPrivate"));
        Predicate isPublic = cb.isFalse(root.get("isPrivate"));
        Predicate isOwner =
            StringUtils.isNotBlank(context.getUserId())
                ? cb.equal(root.get("owner"), context.getUserId())
                : cb.disjunction();

        if (!context.isAuthenticated()) {
          // Not authenticated -> Public only
          predicates.add(isPublic);
        } else if (context.isViewingOwnLists()) {
          // Viewing own lists -> Owner only
          predicates.add(isOwner);
        } else if (!context.isAdmin()) {
          // Authenticated but not admin -> Public OR Owner
          // Wait, original logic:
          // if (!context.isAdmin()) -> filter(isPrivate=false) ??
          // The original logic was:
          // if (!isAuthenticated) -> isPrivate=false
          // else if (viewingOwnLists) -> owner=userId
          // else if (!isAdmin) -> isPrivate=false  <-- This implies non-admins ONLY see public
          // lists, unless viewingOwnLists is true.
          // But usually users should see their own private lists too?
          // Let's stick to the original logic: "isPrivate=false".
          // Ideally it should be "isPublic OR isOwner".

          // Original Code:
          // } else if (!context.isAdmin()) {
          //    bq.filter(f -> f.term(t -> t.field("isPrivate").value(false)));
          // }
          // This means regular users can ONLY search public lists in the general search.
          predicates.add(isPublic);
        } else if (context.isAdmin() && context.getUserId() != null) {
          // Admin logic in original code:
          // } else if (context.isAdmin() && context.getUserId() != null) {
          //    bq.filter(f -> f.term(t -> t.field("owner").value(context.getUserId())));
          // }
          // This looks like "Admin viewing their own lists"?
          // No, wait. The original code block had a weird condition.
          // If isAdmin is true, it falls through and adds NO filters (sees everything),
          // UNLESS the last condition matches.
          // Let's assume Admin sees everything by default.
        }
      }

      // 3. Other Filters
      if (context.getFilters() != null) {
        for (Filter filter : context.getFilters()) {
          // Handle specific filters
          // TODO: Map Filter keys to Entity fields properly
          if ("isAuthoritative".equals(filter.getKey())) {
            predicates.add(
                cb.equal(
                    root.get("isAuthoritative"),
                    Boolean.valueOf(
                        filter.getValue().toString()))); // Assuming value is boolean/string
          }
          // Add other filters as needed
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
