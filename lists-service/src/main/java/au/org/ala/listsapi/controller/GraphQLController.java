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
package au.org.ala.listsapi.controller;

import java.net.URI;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.model.Facet;
import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.model.Image;
import au.org.ala.listsapi.model.InputSpeciesListItem;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.ListSearchContext;
import au.org.ala.listsapi.model.PermissionContext;
import au.org.ala.listsapi.model.Release;
import au.org.ala.listsapi.model.SingleListSearchContext;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListIndex;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.ReleaseMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListIndexElasticRepository;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.MetadataService;
import au.org.ala.listsapi.service.SearchHelperService;
import au.org.ala.listsapi.service.TaxonService;
import au.org.ala.listsapi.service.ValidationService;
import au.org.ala.listsapi.util.ElasticUtils;
import au.org.ala.ws.security.profile.AlaUserProfile;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

/**
 * GraphQL API for lists
 */
@Controller
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "GraphQL", description = "GraphQL Services for species lists lookups")
public class GraphQLController {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLController.class);

    @Value("${elastic.maximumDocuments}")
    public static final int MAX_LIST_ENTRIES = 10000;

    @Value("${image.url}")
    private String imageTemplateUrl;

    @Value("${bie.url}")
    private String bieTemplateUrl;

    @Value("${bie.images.url}")
    private String bieImagesTemplateUrl;

    public static final List<String> CORE_FIELDS = List.of(
            "id",
            "suppliedName",
            "scientificName",
            "vernacularName",
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
            "isThreatened",
            "isInvasive",
            "isPrivate",
            "hasRegion",
            "isSDS",
            "tags");

    public static final String SPECIES_LIST_ID = "speciesListID";
    @Autowired
    protected SpeciesListMongoRepository speciesListMongoRepository;
    @Autowired
    protected ReleaseMongoRepository releaseMongoRepository;
    @Autowired
    protected ElasticsearchOperations elasticsearchOperations;
    @Autowired
    protected SpeciesListIndexElasticRepository speciesListIndexElasticRepository;
    @Autowired
    protected SpeciesListItemMongoRepository speciesListItemMongoRepository;
    @Autowired
    protected SearchHelperService searchHelperService;

    @Autowired
    protected TaxonService taxonService;
    @Autowired
    protected ValidationService validationService;
    @Autowired
    protected AuthUtils authUtils;
    @Autowired
    protected MetadataService metadataService;

    static Date parsedDate(String date) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Browse & search across lists with proper permission handling
     */
    @QueryMapping
    public Page<SpeciesList> lists(
            @Argument String searchQuery,
            @Argument List<Filter> filters,
            @Argument Integer page,
            @Argument Integer size,
            @Argument String userId,
            @Argument String sort,
            @Argument String dir,
            @AuthenticationPrincipal Principal principal) {

        // Build search context with permission checks
        ListSearchContext searchContext = buildSearchContext(
            searchQuery, filters, userId, sort, dir, principal
        );
        
        // Delegate to service for the actual search
        return searchHelperService.searchSpeciesLists(
            searchContext, 
            PageRequest.of(page, size)
        );
    }

    /**
     * Get facets for species lists with proper permission handling
     */
    @QueryMapping
    public List<Facet> facetSpeciesLists(
            @Argument String searchQuery,
            @Argument List<Filter> filters,
            @Argument String userId,
            @Argument Integer page,
            @Argument Integer size,
            @AuthenticationPrincipal Principal principal) {

        // Build search context with permission checks
        ListSearchContext searchContext = buildSearchContext(
            searchQuery, filters, userId, null, null, principal
        );
        
        // Delegate to service for facet aggregation
        return searchHelperService.getFacetsForSpeciesLists(searchContext);
    }

    /**
     * Builds a search context with proper permission validation
     */
    private ListSearchContext buildSearchContext(
            String searchQuery,
            List<Filter> filters,
            String userId,
            String sort,
            String dir,
            Principal principal) {
        
        AlaUserProfile profile = authUtils.getUserProfile(principal);
        PermissionContext permissions = determinePermissions(userId, filters, profile);
        
        return ListSearchContext.builder()
            .searchQuery(ElasticUtils.cleanRawQuery(searchQuery))
            .filters(filters != null ? filters : new ArrayList<>())
            .userId(permissions.getEffectiveUserId())
            .sort(StringUtils.defaultIfBlank(sort, "relevance"))
            .dir(StringUtils.defaultIfBlank(dir, "desc"))
            .isAdmin(permissions.isAdmin())
            .isAuthenticated(permissions.isAuthenticated())
            .isViewingOwnLists(permissions.isViewingOwnLists())
            .build();
    }

    /**
     * Determines user permissions and validates access
     */
    private PermissionContext determinePermissions(
            String userId, 
            List<Filter> filters, 
            AlaUserProfile profile) {
        boolean isAuthenticated = profile != null;
        boolean isAdmin = isAuthenticated && 
            (authUtils.hasAdminRole(profile) || authUtils.hasInternalScope(profile));
        
        String currentUserId = isAuthenticated && profile != null ? profile.getUserId() : null;
        boolean isViewingOwnLists = userId != null && userId.equals(currentUserId);
        
        // Validate access when userId is specified
        if (userId != null && !isAdmin && !isViewingOwnLists) {
            logger.warn("User {} attempted to access lists for user {}", currentUserId, userId);
            throw new AccessDeniedException("You can only view your own lists");
        }
        
        // Check for private filter
        boolean requestingPrivate = hasPrivateFilter(filters);
        if (requestingPrivate && !isAuthenticated) {
            throw new AccessDeniedException("You must be logged in to view private lists");
        }
        
        return PermissionContext.builder()
            .isAuthenticated(isAuthenticated)
            .isAdmin(isAdmin)
            .currentUserId(currentUserId)
            .effectiveUserId(userId)
            .isViewingOwnLists(isViewingOwnLists)
            .build();
    }

    /**
     * Checks if filters contain a private list request
     */
    private boolean hasPrivateFilter(List<Filter> filters) {
        if (filters == null) return false;
        return filters.stream()
            .anyMatch(f -> "isPrivate".equals(f.getKey()) && "true".equals(f.getValue()));
    }

    /**
     * Determines the userId to be used for filtering based on the user's role and privacy settings.
     * @param filters
     * @param isPrivate
     * @return Boolean indicating if the isPrivate filter is applied, null if no filtering
     */
    @NotNull
    private Boolean getPrivateFilterOrFlag(List<Filter> filters, Boolean isPrivate) {
        if ((filters == null || filters.isEmpty()) && isPrivate == null) {
            return null;
        }
        
        boolean hasPrivateFilter = filters != null && filters.stream()
            .anyMatch(f -> f.getKey().equals("isPrivate") && f.getValue().equals("true"));
        
        return hasPrivateFilter || (isPrivate != null && isPrivate);
    }
    
    /**
     * Determines if the isPrivate filter is applied based on the provided filters and isPrivate argument.
     * <p>
     * <b>Special logic for admins:</b> If the user is an admin and no private filter or flag is set,
     * this method returns {@code null}. This signals downstream queries (such as Elasticsearch queries)
     * to not filter on the {@code isPrivate} field at all, allowing admins to see both private and public lists.
     * For non-admins, or if a private filter/flag is set, this method returns {@code true} if a private filter or flag is set,
     * or {@code false} otherwise. This ensures that non-admin users are always subject to privacy filtering,
     * while admins have unrestricted access unless a filter is explicitly applied.
     * <p>
     * <b>Downstream effect:</b> Returning {@code null} for admins means that the query will not include any
     * constraint on the {@code isPrivate} field, so all lists (public and private) are visible to admins.
     *
     * @param filters   List of filters applied to the query.
     * @param isPrivate Boolean flag indicating if private lists are requested.
     * @param isAdmin   Boolean indicating if the current user is an admin.
     * @return {@code true} if the isPrivate filter is applied, {@code false} if not, or {@code null} for admins with no private filter (no filtering).
     */
    @Nullable
    private Boolean isPrivateFilterApplied(List<Filter> filters, Boolean isPrivate, Boolean isAdmin, Boolean isMySpeciesList) {
        boolean defaultValue = false;
        Boolean hasPrivateFilterOrFlag = getPrivateFilterOrFlag(filters, isPrivate); // can be null, true or false
        // boolean isPrivateSpecified = isPrivate != null && isPrivate;
        boolean isAdminSpecified = isAdmin != null && isAdmin;
        // If the user is an admin and no private filter or flag is set, return null (no filtering on isPrivate).
        // Otherwise, return true if a private filter or flag is set, or the default value (false).
        Boolean returnValue;
        if ((isAdminSpecified || isMySpeciesList) && hasPrivateFilterOrFlag == null) {
            returnValue = null;
        } else {
            returnValue = hasPrivateFilterOrFlag != null && hasPrivateFilterOrFlag || defaultValue;
        }

        return returnValue;
    }

    @GraphQlExceptionHandler
    public GraphQLError handle(@NonNull Throwable ex, @NonNull DataFetchingEnvironment environment) {
        return GraphQLError
                .newError()
                .errorType(ErrorType.ValidationError)
                .message(ex.getMessage())
                .path(environment.getExecutionStepInfo().getPath())
                .location(environment.getField().getSourceLocation())
                .build();
    }

    @QueryMapping
    public Page<Release> listReleases(
            @Argument String speciesListID, @Argument Integer page, @Argument Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return releaseMongoRepository.findBySpeciesListID(speciesListID, pageable);
    }

    @QueryMapping
    public SpeciesList getSpeciesListMetadata(
            @Argument String speciesListID, @AuthenticationPrincipal Principal principal) {
        Optional<SpeciesList> speciesListOptional = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID,
                speciesListID);
        if (speciesListOptional.isPresent()) {
            SpeciesList speciesList = speciesListOptional.get();

            // private list, check user is authorized
            if (speciesList.getIsPrivate()
                    && !authUtils.isAuthorized(speciesList, principal)) {
                logger.info("User not authorized to private access list: " + speciesListID);
                throw new AccessDeniedException("You don't have access to this list");
            }

            return speciesList;
        }

        return null;
    }

    @SchemaMapping(typeName = "Mutation", field = "addField")
    public SpeciesList addField(
            @Argument String id,
            @Argument String fieldName,
            @Argument String fieldValue,
            @AuthenticationPrincipal Principal principal) {

        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

        if (optionalSpeciesList.isEmpty()) {
            return null;
        }

        SpeciesList toUpdate = optionalSpeciesList.get();

        if (!authUtils.isAuthorized(toUpdate, principal)) {
            logger.info("User not authorized to modify access list: " + id);
            throw new AccessDeniedException("You dont have authorisation to modify this list");
        }

        toUpdate.getFieldList().add(fieldName);

        if (StringUtils.isNotEmpty(fieldValue)) {
            int batchSize = MAX_LIST_ENTRIES;
            ObjectId lastId = null;

            boolean finished = false;
            while (!finished) {
                List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(toUpdate.getId(), lastId,
                        PageRequest.of(0, batchSize));

                for (SpeciesListItem item : items) {
                    item.getProperties().add(new KeyValue(fieldName, fieldValue));
                }

                if (!items.isEmpty()) {
                    searchHelperService.speciesListItemsBulkUpdate(items, List.of("properties"));
                }

                if (items.size() < batchSize) {
                    finished = true;
                } else {
                    lastId = items.get(items.size() - 1).getId();
                }
            }
        }

        return speciesListMongoRepository.save(toUpdate);
    }

    @SchemaMapping(typeName = "Mutation", field = "renameField")
    public SpeciesList renameField(
            @Argument String id,
            @Argument String oldName,
            @Argument String newName,
            @AuthenticationPrincipal Principal principal) {

        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

        if (optionalSpeciesList.isEmpty()) {
            return null;
        }

        SpeciesList toUpdate = optionalSpeciesList.get();

        if (!authUtils.isAuthorized(toUpdate, principal)) {
            logger.info("User not authorized to modify access list: " + id);
            throw new AccessDeniedException("You dont have access to this list");
        }

        // remove from species list metadata
        toUpdate.getFieldList().remove(oldName);
        toUpdate.getFieldList().add(newName);

        int batchSize = MAX_LIST_ENTRIES;
        ObjectId lastId = null;

        boolean finished = false;
        while (!finished) {
            List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(toUpdate.getId(), lastId,
                    PageRequest.of(0, batchSize));

            for (SpeciesListItem item : items) {
                Optional<KeyValue> kv = item.getProperties().stream().filter(k -> k.getKey().equals(oldName))
                        .findFirst();
                kv.ifPresent(keyValue -> keyValue.setKey(newName));
            }

            if (!items.isEmpty()) {
                searchHelperService.speciesListItemsBulkUpdate(items, List.of("properties"));
            }

            if (items.size() < batchSize) {
                finished = true;
            } else {
                lastId = items.get(items.size() - 1).getId();
            }
        }
        return speciesListMongoRepository.save(toUpdate);
    }

    @SchemaMapping(typeName = "Mutation", field = "removeField")
    public SpeciesList removeField(
            @Argument String id,
            @Argument String fieldName,
            @AuthenticationPrincipal Principal principal) {

        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

        if (optionalSpeciesList.isEmpty()) {
            return null;
        }

        SpeciesList toUpdate = optionalSpeciesList.get();

        if (!authUtils.isAuthorized(toUpdate, principal)) {
            logger.info("User not authorized to modify access list: " + id);
            throw new AccessDeniedException("You dont have access to this list");
        }

        toUpdate.getFieldList().remove(fieldName);

        int batchSize = MAX_LIST_ENTRIES;
        ObjectId lastId = null;

        boolean finished = false;
        while (!finished) {
            List<SpeciesListItem> items = speciesListItemMongoRepository.findNextBatch(toUpdate.getId(), lastId,
                    PageRequest.of(0, batchSize));

            for (SpeciesListItem item : items) {
                Optional<KeyValue> kv = item.getProperties().stream().filter(k -> k.getKey().equals(fieldName))
                        .findFirst();
                kv.ifPresent(keyValue -> item.getProperties().remove(keyValue));
            }

            if (!items.isEmpty()) {
                searchHelperService.speciesListItemsBulkUpdate(items, List.of("properties"));
            }

            if (items.size() < batchSize) {
                finished = true;
            } else {
                lastId = items.get(items.size() - 1).getId();
            }
        }

        return speciesListMongoRepository.save(toUpdate);
    }

    @SchemaMapping(typeName = "Mutation", field = "updateSpeciesListItem")
    public SpeciesListItem updateSpeciesListItem(
            @Argument InputSpeciesListItem inputSpeciesListItem,
            @AuthenticationPrincipal Principal principal) {

        Optional<SpeciesListItem> optionalSpeciesListItem = speciesListItemMongoRepository
                .findById(inputSpeciesListItem.getId());
        if (optionalSpeciesListItem.isEmpty()) {
            return null;
        }

        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository
                .findById(inputSpeciesListItem.getSpeciesListID());

        if (optionalSpeciesList.isEmpty()) {
            return null;
        }

        if (!authUtils.isAuthorized(optionalSpeciesList.get(), principal)) {
            logger.info(
                    "User not authorized to modify access list: " + optionalSpeciesList.get().getId());
            throw new AccessDeniedException("You dont have access to this list");
        }

        SpeciesList speciesList = optionalSpeciesList.get();
        SpeciesListItem speciesListItem = optionalSpeciesListItem.get();
        updateItem(inputSpeciesListItem, speciesListItem, principal);

        // update last updated
        speciesList = updateLastUpdated(speciesList, principal);

        // rematch taxonomy
        try {
            Classification classification = taxonService.lookupTaxon(speciesListItem);
            speciesListItem.setClassification(classification);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        // reindex the item
        reindex(speciesListItem, speciesList);

        // update distinct match count
        speciesList.setDistinctMatchCount(taxonService.getDistinctTaxaCount(speciesList.getId()));
        speciesListMongoRepository.save(speciesList);

        logger.info("Updated species list item: " + speciesListItem.getId());
        return speciesListItem;
    }

    private SpeciesListItem updateItem(
            InputSpeciesListItem inputSpeciesListItem, SpeciesListItem speciesListItem, Principal principal) {
        speciesListItem.setScientificName(inputSpeciesListItem.getScientificName());
        speciesListItem.setTaxonID(inputSpeciesListItem.getTaxonID());
        speciesListItem.setGenus(inputSpeciesListItem.getGenus());
        speciesListItem.setFamily(inputSpeciesListItem.getFamily());
        speciesListItem.setOrder(inputSpeciesListItem.getOrder());
        speciesListItem.setClasss(inputSpeciesListItem.getClasss());
        speciesListItem.setPhylum(inputSpeciesListItem.getPhylum());
        speciesListItem.setKingdom(inputSpeciesListItem.getKingdom());
        speciesListItem.setVernacularName(inputSpeciesListItem.getVernacularName());
        speciesListItem.setProperties(
                inputSpeciesListItem.getProperties().stream()
                        .map(kv -> new KeyValue(kv.getKey(), kv.getValue()))
                        .collect(Collectors.toList()));
        speciesListItem.setLastUpdated(new Date());
        speciesListItem.setLastUpdatedBy(principal.getName());
        if (speciesListItem.getSpeciesListID() == null) {
            speciesListItem.setSpeciesListID(inputSpeciesListItem.getSpeciesListID());
        }
        return speciesListItemMongoRepository.save(speciesListItem);
    }

    private void reindex(SpeciesListItem speciesListItem, SpeciesList speciesList) {
        // write the data to Elasticsearch
        SpeciesListIndex speciesListIndex = new SpeciesListIndex(
                speciesListItem.getId().toString(),
                speciesList.getDataResourceUid(),
                speciesList.getTitle(),
                speciesList.getListType(),
                speciesListItem.getSpeciesListID(),
                speciesList.getDescription(),
                speciesList.getLicence(),
                speciesListItem.getSuppliedName(),
                speciesListItem.getScientificName(),
                speciesListItem.getVernacularName(),
                speciesListItem.getTaxonID(),
                speciesListItem.getKingdom(),
                speciesListItem.getPhylum(),
                speciesListItem.getClasss(),
                speciesListItem.getOrder(),
                speciesListItem.getFamily(),
                speciesListItem.getGenus(),
                speciesListItem.getProperties(),
                speciesListItem.getClassification(),
                speciesList.getIsPrivate() != null ? speciesList.getIsPrivate() : false,
                speciesList.getIsAuthoritative() != null ? speciesList.getIsAuthoritative() : false,
                speciesList.getIsBIE() != null ? speciesList.getIsBIE() : false,
                speciesList.getIsSDS() != null ? speciesList.getIsSDS() : false,
                speciesList.getIsThreatened() != null ? speciesList.getIsThreatened() : false,
                speciesList.getIsInvasive() != null ? speciesList.getIsInvasive() : false,
                StringUtils.isNotEmpty(speciesList.getRegion()) || StringUtils.isNotEmpty(speciesList.getWkt()),
                speciesList.getOwner(),
                speciesList.getEditors(),
                speciesList.getTags(),
                speciesList.getDateCreated() != null ? speciesList.getDateCreated().toString() : null,
                speciesList.getLastUpdated() != null ? speciesList.getLastUpdated().toString() : null,
                speciesList.getLastUpdatedBy());

        speciesListIndexElasticRepository.save(speciesListIndex);
    }

    @SchemaMapping(typeName = "Mutation", field = "addSpeciesListItem")
    public SpeciesListItem addSpeciesListItem(
            @Argument InputSpeciesListItem inputSpeciesListItem,
            @AuthenticationPrincipal Principal principal) {

        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository
                .findById(inputSpeciesListItem.getSpeciesListID());

        if (optionalSpeciesList.isEmpty()) {
            return null;
        }

        SpeciesList speciesList = optionalSpeciesList.get();

        if (!authUtils.isAuthorized(speciesList, principal)) {
            throw new AccessDeniedException("You dont have access to this list");
        }

        // add the new entry
        SpeciesListItem speciesListItem = new SpeciesListItem();
        speciesListItem = updateItem(inputSpeciesListItem, speciesListItem, principal);

        // update last updated
        speciesList = updateLastUpdated(speciesList, principal);

        // rematch taxonomy
        try {
            Classification classification = taxonService.lookupTaxon(speciesListItem);
            speciesListItem.setClassification(classification);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        // index
        reindex(speciesListItem, optionalSpeciesList.get());

        // update distinct match count
        speciesList.setDistinctMatchCount(taxonService.getDistinctTaxaCount(speciesList.getId()));
        speciesList.setRowCount(speciesList.getRowCount() + 1);
        speciesListMongoRepository.save(speciesList);

        return speciesListItem;
    }

    private SpeciesList updateLastUpdated(SpeciesList speciesList, Principal principal) {
        speciesList.setLastUpdated(new Date());
        speciesList.setLastUpdatedBy(principal.getName());
        return speciesListMongoRepository.save(speciesList);
    }

    @SchemaMapping(typeName = "Mutation", field = "removeSpeciesListItem")
    public SpeciesListItem removeSpeciesListItem(
            @Argument String id, @AuthenticationPrincipal Principal principal) {

        Optional<SpeciesListItem> optionalSpeciesListItem = speciesListItemMongoRepository.findById(id);
        if (optionalSpeciesListItem.isEmpty()) {
            return null;
        }

        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository
                .findById(optionalSpeciesListItem.get().getSpeciesListID());
        if (optionalSpeciesList.isEmpty()) {
            return null;
        }

        SpeciesList speciesList = optionalSpeciesList.get();

        if (!authUtils.isAuthorized(optionalSpeciesList.get(), principal)) {
            throw new AccessDeniedException("You dont have access to this list");
        }

        // delete the list item
        speciesListItemMongoRepository.deleteById(id);
        speciesListIndexElasticRepository.deleteById(id);

        // update distinct match count
        speciesList.setDistinctMatchCount(taxonService.getDistinctTaxaCount(speciesList.getId()));
        speciesList.setRowCount(speciesList.getRowCount() - 1);
        speciesListMongoRepository.save(speciesList);

        return optionalSpeciesListItem.get();
    }

    @SchemaMapping(typeName = "Mutation", field = "updateMetadata")
    public SpeciesList updateMetadata(
            @Argument String id,
            @Argument String title,
            @Argument String description,
            @Argument String licence,
            @Argument String listType,
            @Argument String authority,
            @Argument String region,
            @Argument String wkt,
            @Argument Boolean isPrivate,
            @Argument Boolean isThreatened,
            @Argument Boolean isInvasive,
            @Argument Boolean isAuthoritative,
            @Argument Boolean isSDS,
            @Argument Boolean isBIE,
            @Argument List<String> tags,
            @AuthenticationPrincipal Principal principal) throws Exception {
        Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(id, id);

        if (optionalSpeciesList.isEmpty()) {
            return null;
        }

        SpeciesList toUpdate = optionalSpeciesList.get();

        if (authUtils.isAuthorized(toUpdate, principal)) {
            boolean reindexRequired = false;

            // check that the supplied list type, region and license is valid
            if (!validationService.isValueValid(ConstraintType.listType, listType) ||
                    !validationService.isValueValid(ConstraintType.licence, licence)) {
                throw new Exception(
                        "Updated list contains invalid properties for a controlled value (list type, license)");
            }

            if (title != null && !title.equalsIgnoreCase(toUpdate.getTitle())
                    || description != null && !description.equalsIgnoreCase(toUpdate.getDescription())
                    || listType != null && !listType.equalsIgnoreCase(toUpdate.getListType())
                    || isPrivate != null && !isPrivate.equals(toUpdate.getIsPrivate())
                    || isAuthoritative != null && !isAuthoritative.equals(toUpdate.getIsAuthoritative())
                    || isBIE != null && !isBIE.equals(toUpdate.getIsBIE())
                    || isSDS != null && !isSDS.equals(toUpdate.getIsSDS())
                    || isInvasive != null && !isInvasive.equals(toUpdate.getIsInvasive())
                    || isThreatened != null && !isThreatened.equals(toUpdate.getIsThreatened())
                    || wkt != null && !wkt.equals(toUpdate.getWkt())
                    || region != null && !region.equals(toUpdate.getRegion())
                    || licence != null && !licence.equals(toUpdate.getLicence())
                    || tags != null && !tags.equals(toUpdate.getTags())) {
                reindexRequired = true;
            }

            boolean previousIsPrivate = toUpdate.getIsPrivate() != null ? toUpdate.getIsPrivate() : false;
            toUpdate.setTitle(title);
            toUpdate.setDescription(description);
            toUpdate.setLicence(licence);
            toUpdate.setListType(listType);
            toUpdate.setAuthority(authority);
            toUpdate.setRegion(region);
            toUpdate.setIsPrivate(isPrivate);
            toUpdate.setIsThreatened(isThreatened);
            toUpdate.setIsInvasive(isInvasive);
            toUpdate.setIsAuthoritative(isAuthoritative);
            toUpdate.setIsBIE(isBIE);
            toUpdate.setIsSDS(isSDS);
            toUpdate.setWkt(wkt);
            toUpdate.setLastUpdatedBy(principal.getName());
            toUpdate.setTags(tags);

            try {
                if (Boolean.FALSE.equals(toUpdate.getIsPrivate()) // saved list is public
                        || Boolean.TRUE.equals(toUpdate.getIsAuthoritative()) // saved list is authoritative (private or public)
                        || (!previousIsPrivate && Boolean.TRUE.equals(isPrivate)) // was public, now private
                ) {
                    metadataService.setMeta(toUpdate);
                }
            } catch (Exception e) {
                logger.error("Error while setting metadata for species list: " + id + " - " + e.getMessage(), e);
            }

            // If the visibility has changed, update the visibility of the list items
            // in elasticsearch and mongo
            SpeciesList updatedList = speciesListMongoRepository.save(toUpdate);
            if (reindexRequired) {
                logger.debug("Reindexing list {} after metadata update. isPrivate changed to: {}", 
                        updatedList.getId(), updatedList.getIsPrivate());
                taxonService.reindex(updatedList.getId());
            }

            return updatedList;
        } else {
            throw new AccessDeniedException("You dont have access to this list");
        }
    }

    /**
     * Get species list items with filtering and pagination
     */
    @QueryMapping
    public Page<SpeciesListItem> getSpeciesList(
            @Argument String speciesListID,
            @Argument Integer page,
            @Argument Integer size,
            @AuthenticationPrincipal Principal principal) {
        return filterSpeciesList(
            speciesListID, null, new ArrayList<>(), page, size, null, null, principal
        );
    }

    /**
     * Filter species list items with search query and filters
     */
    @QueryMapping
    public Page<SpeciesListItem> filterSpeciesList(
            @Argument String speciesListID,
            @Argument String searchQuery,
            @Argument List<Filter> filters,
            @Argument Integer page,
            @Argument Integer size,
            @Argument String sort,
            @Argument String dir,
            @AuthenticationPrincipal Principal principal) {

        // Validate and authorize access to the list
        SpeciesList speciesList = validateAndAuthorizeListAccess(speciesListID, principal);
        if (speciesList == null) {
            return null;
        }

        // Build search context for the specific list
        SingleListSearchContext searchContext = buildSingleListSearchContext(
            speciesList,
            searchQuery,
            filters,
            sort,
            dir,
            principal
        );

        // Delegate to service for the actual search
        return searchHelperService.searchSingleSpeciesList(
            searchContext,
            PageRequest.of(page, size)
        );
    }

    /**
     * Get facets for a single species list
     */
    @QueryMapping
    public List<Facet> facetSpeciesList(
            @Argument String speciesListID,
            @Argument String searchQuery,
            @Argument List<Filter> filters,
            @Argument List<String> facetFields,
            @Argument Integer page,
            @Argument Integer size,
            @AuthenticationPrincipal Principal principal) {

        // Validate and authorize access to the list
        SpeciesList speciesList = validateAndAuthorizeListAccess(speciesListID, principal);
        if (speciesList == null) {
            return null;
        }

        // Build search context for the specific list
        SingleListSearchContext searchContext = buildSingleListSearchContext(
            speciesList,
            searchQuery,
            filters,
            null,
            null,
            principal
        );

        // Use provided facet fields or default to list's configured facets
        List<String> effectiveFacetFields = (facetFields != null && !facetFields.isEmpty())
            ? facetFields
            : speciesList.getFacetList();

        // Delegate to service for facet aggregation
        return searchHelperService.getFacetsForSingleSpeciesList(
            searchContext,
            effectiveFacetFields
        );
    }

    /**
     * Validates list exists and user has permission to access it
     */
    private SpeciesList validateAndAuthorizeListAccess(
            String speciesListID,
            Principal principal) {
        
        Optional<SpeciesList> optionalSpeciesList = 
            speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
        
        if (optionalSpeciesList.isEmpty()) {
            logger.warn("Species list not found: {}", speciesListID);
            return null;
        }

        SpeciesList speciesList = optionalSpeciesList.get();

        // Check if user has access to private list
        if (speciesList.getIsPrivate() && !authUtils.isAuthorized(speciesList, principal)) {
            logger.info("User not authorized to access private list: {}", speciesListID);
            throw new AccessDeniedException("You don't have access to this list");
        }

        return speciesList;
    }

    /**
     * Builds search context for a specific list with permission validation
     */
    private SingleListSearchContext buildSingleListSearchContext(
            SpeciesList speciesList,
            String searchQuery,
            List<Filter> filters,
            String sort,
            String dir,
            Principal principal) {
        
        AlaUserProfile profile = authUtils.getUserProfile(principal);
        boolean isAdmin = profile != null && 
            (authUtils.hasAdminRole(profile) || authUtils.hasInternalScope(profile));
        String userId = profile != null ? profile.getUserId() : null;

        return SingleListSearchContext.builder()
            .speciesListId(speciesList.getId())
            .speciesList(speciesList)
            .searchQuery(ElasticUtils.cleanRawQuery(searchQuery))
            .filters(filters != null ? filters : new ArrayList<>())
            .userId(userId)
            .sort(StringUtils.defaultIfBlank(sort, "scientificName"))
            .dir(StringUtils.defaultIfBlank(dir, "asc"))
            .isAdmin(isAdmin)
            .build();
    }

    @NotNull
    private static String cleanRawQuery(String searchQuery) {
        if (searchQuery != null)
            return searchQuery.trim().replace("\"", "\\\"");
        return "";
    }

    @QueryMapping
    public Image getTaxonImage(@Argument String taxonID) throws Exception {
        // get taxon image from BIE
        // https://bie.ala.org.au/ws/taxon/https://id.biodiversity.org.au/node/apni/2910201
        //
        // https://bie.ala.org.au/ws/imageSearch/https%3A//id.biodiversity.org.au/taxon/apni/51288314?rows=5&start=0

        Map<String, Object> bieJson = loadJson(String.format(bieTemplateUrl, taxonID));
        if (bieJson != null) {
            String imageID = (String) bieJson.getOrDefault("imageIdentifier", null);
            if (imageID != null) {
                return new Image(String.format(imageTemplateUrl, imageID));
            }
        }
        return null;
    }

    @QueryMapping
    public List<Image> getTaxonImages(
            @Argument String taxonID, @Argument Integer page, @Argument Integer size) throws Exception {
        // get taxon image from BIE
        ObjectMapper objectMapper = new ObjectMapper();
        String url = String.format(bieImagesTemplateUrl, taxonID, size, page * size);
        JsonNode jsonNode = objectMapper.readTree(new URI(url).toURL());
        JsonNode results = jsonNode.at("/searchResults/results");
        List<Image> images = new ArrayList<>();
        Iterator<JsonNode> iter = results.elements();
        while (iter.hasNext()) {
            JsonNode node = iter.next();
            images.add(new Image(node.get("largeImageUrl").asText()));
        }
        return images;
    }

    public Map<String, Object> loadJson(String url) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new URI(url).toURL(), objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }
}
