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

package au.org.ala.listsapi.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import au.org.ala.listsapi.model.AbbrListVersion1;
import au.org.ala.listsapi.model.KvpValueVersion1;
import au.org.ala.listsapi.model.QueryListItemVersion1;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesItemVersion1;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListItemVersion1;
import au.org.ala.listsapi.model.SpeciesListVersion1;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.UserdetailsService;

/**
 * Transformer utility to convert a SpeciesList to a legacy SpeciesListVersion1 format
 * or a SpeciesListItem to a legacy SpeciesListItemVersion1 format.
 * This maintains backward compatibility with legacy applications via `/v1` endpoints.
 */
@Component
public class SpeciesListTransformer {
    private static final Logger logger = LoggerFactory.getLogger(SpeciesListTransformer.class);
    private final UserdetailsService userdetailsService;
    private final SpeciesListMongoRepository speciesListMongoRepository;

    @Autowired
    public SpeciesListTransformer(UserdetailsService userdetailsService, SpeciesListMongoRepository speciesListMongoRepository) {
        this.userdetailsService = userdetailsService;
        this.speciesListMongoRepository = speciesListMongoRepository;
    }

    @Value("${legacy.lookup.users.enabled:false}")
    private Boolean legacyLookupUsersEnabled;

    /**
     * Transforms a SpeciesList object to a SpeciesListVersion1 object.
     *
     * @param speciesList The source SpeciesList object
     * @return A new SpeciesListVersion1 object populated with values from the source
     */
    public SpeciesListVersion1 transformToVersion1(SpeciesList speciesList) {
        if (speciesList == null) {
            return null;
        }

        SpeciesListVersion1 version1 = new SpeciesListVersion1();

        // Map properties from SpeciesList to SpeciesListVersion1
        version1.setId(speciesList.getId());
        version1.setDataResourceUid(speciesList.getDataResourceUid());
        version1.setDateCreated(speciesList.getDateCreated());
        version1.setIsAuthoritative(speciesList.getIsAuthoritative());
        version1.setIsInvasive(speciesList.getIsInvasive());
        version1.setIsThreatened(speciesList.getIsThreatened());
        version1.setIsPrivate(speciesList.getIsPrivate());
        version1.setIsSDS(speciesList.getIsSDS());
        version1.setIsBIE(speciesList.getIsBIE());
        version1.setItemCount(speciesList.getRowCount());

        version1.setDateCreated(speciesList.getDateCreated());
        version1.setLastUploaded(speciesList.getLastUploaded());
        version1.setLastMatched(speciesList.getLastUpdated()); // Not strictly true but good enough for legacy services
        version1.setLastUpdated(speciesList.getLastUpdated());

        version1.setListName(speciesList.getTitle());
        version1.setDescription(speciesList.getDescription());
        version1.setListType(speciesList.getListType());
        version1.setAuthority(speciesList.getAuthority() != null ? speciesList.getAuthority() : "");
        version1.setCategory(speciesList.getCategory() != null ? speciesList.getCategory() : "");
        version1.setRegion(speciesList.getRegion());
        version1.setSdsType(Boolean.TRUE.equals(speciesList.getIsSDS()) ? "CONSERVATION" : "");
        version1.setGeneralisation(Boolean.TRUE.equals(speciesList.getIsSDS()) ? "10km" : ""); // Not implemented in SpeciesList but returning empty string for legacy support
        version1.setWkt(speciesList.getWkt());

        // Fetch user details using the userdetailsService if enabled, and the owner is not an email address (e.g., a userID number)
        if (legacyLookupUsersEnabled && speciesList.getOwner() != null && !speciesList.getOwner().isEmpty()
                && !speciesList.getOwner().contains("@")) {
            Map userDetails = userdetailsService.fetchUserByEmail(speciesList.getOwner());

            if (userDetails != null && userDetails.get("email") != null) {
                version1.setUsername((String) userDetails.get("email"));
                String fullName = (String) userDetails.get("displayName");
                if (fullName == null || fullName.trim().isEmpty()) {
                    String fName = userDetails.get("firstName") != null ? ((String) userDetails.get("firstName")).trim() : "";
                    String lName = userDetails.get("lastName") != null ? ((String) userDetails.get("lastName")).trim() : "";
                    if (!fName.isEmpty() && !lName.isEmpty()) {
                        fullName = fName + " " + lName;
                    } else if (!fName.isEmpty()) {
                        fullName = fName;
                    } else if (!lName.isEmpty()) {
                        fullName = lName;
                    }
                }
                version1.setFullName(fullName);
            } else {
                version1.setUsername(speciesList.getOwner());
                version1.setFullName(speciesList.getOwnerName());
            }
        } else {
            version1.setUsername(speciesList.getOwner());
            version1.setFullName(speciesList.getOwnerName());
        }

        return version1;
    }

    /**
     * Transforms a SpeciesListItem object to a SpeciesListItemVersion1 object
     *
     * @param speciesListItem The source SpeciesListItem object
     * @param index The index of the item in the list
     * @param listCache A cache of SpeciesList objects keyed by speciesListID to avoid repeated MongoDB lookups
     * @return A new SpeciesListItemVersion1 object populated with values from the source
     */
    public SpeciesListItemVersion1 transformToVersion1(SpeciesListItem speciesListItem, int index, Map<String, Optional<SpeciesList>> listCache) {
        if (speciesListItem == null) {
            return null;
        }

        SpeciesListItemVersion1 listItemVersion1 = new SpeciesListItemVersion1();
        String speciesListID = speciesListItem.getSpeciesListID();
        listItemVersion1.setId(toLegacyId(speciesListItem));
        listItemVersion1.setLsid(speciesListItem.getClassification() != null ? speciesListItem.getClassification().getTaxonConceptID() : null);
        listItemVersion1.setScientificName(speciesListItem.getClassification() != null ? speciesListItem.getClassification().getScientificName() : speciesListItem.getScientificName());
        listItemVersion1.setCommonName(speciesListItem.getVernacularName() != null ? speciesListItem.getVernacularName() : speciesListItem.getClassification() != null ? speciesListItem.getClassification().getVernacularName() : null);
        listItemVersion1.setName(speciesListItem.getScientificName() != null ? speciesListItem.getScientificName() : speciesListItem.getSuppliedName());
        listItemVersion1.setDataResourceUid(speciesListID); // fallback - attempt to set actual DataResourceUid further down

        // Get list details via MongoDB
        Optional<SpeciesList> speciesList = listCache.computeIfAbsent(speciesListID, id -> speciesListMongoRepository.findByIdOrDataResourceUid(id, id));
        AbbrListVersion1 list = new AbbrListVersion1();

        if (speciesList.isPresent()) {
            SpeciesList speciesListV2 = speciesList.get();
            list.setListName(speciesListV2.getTitle());
            list.setUsername(speciesListV2.getOwner());
            list.setSds(speciesListV2.getIsSDS());
            list.setIsBIE(speciesListV2.getIsBIE());
            listItemVersion1.setDataResourceUid(speciesListV2.getDataResourceUid() == null ? speciesListID : speciesListV2.getDataResourceUid());
        } else {
            logger.warn("SpeciesListItemVersion1 transformToVersion1() -> Species list not found for ID: " + speciesListID);
        }

        List<KvpValueVersion1> kvps = buildKvpValues(speciesListItem.getProperties());
        listItemVersion1.setKvpValues(kvps);

        return listItemVersion1;
    }

    /**
     * Converts the MongoDB ObjectId of a SpeciesListItem to a legacy integer ID for backward compatibility with v1 endpoints.
     * <p>
     * This is a deliberately <strong>lossy</strong> conversion that derives a 32-bit integer from the 96-bit
     * MongoDB ObjectId by taking only the lower 32 bits of its hexadecimal representation. As a result:
     * <ul>
     *   <li>Information from the higher-order bits of the ObjectId is discarded.</li>
     *   <li>Different ObjectIds may map to the same legacy integer ID (collisions are possible).</li>
     * </ul>
     * This method exists solely to satisfy legacy v1 contracts that expect integer IDs and should not be
     * relied upon as a unique or stable identifier beyond that context.
     *
     * @param speciesListItem the source item whose ObjectId is used to derive the legacy ID
     * @return a long value representing the legacy ID (containing the lower 32 bits of the ObjectId)
     */
    private long toLegacyId(SpeciesListItem speciesListItem) {
        BigInteger bigIntId = new BigInteger(speciesListItem.getId().toHexString(), 16);
        // Legacy IDs were simple integers; convert ObjectId to a numeric value to satisfy legacy expectations
        return bigIntId.longValue();
    }

    private static String fixLegacyKeys(String key) {
        // Fix known legacy key names, for backward compatibility
        switch (key) {
            case "CommonNames":
                return "common name";
            case "VernacularName":
                return "vernacular name";
            case "group":
                return "Group";
            case "taxonRank":
                return "rank";
            default:
                return key;
        }
    }
    
    /**
     * Check if a key is a raw taxonomic field (rawkingdom, rawphylum, rawclass, raworder, rawfamily, rawgenus)
     * Case-insensitive comparison to handle variations in field naming
     */
    private static boolean isRawTaxonomicField(String key) {
        if (key == null) return false;
        String lowerKey = key.toLowerCase();
        return lowerKey.equals("rawkingdom") || 
               lowerKey.equals("rawphylum") || 
               lowerKey.equals("rawclass") || 
               lowerKey.equals("raworder") || 
               lowerKey.equals("rawfamily") || 
               lowerKey.equals("rawgenus") ||
               lowerKey.equals("rawrank") ||
               lowerKey.equals("rawscientific_name") ||
               lowerKey.equals("rawsupplied_name") ||
               lowerKey.equals("rawscientificname") ||
               lowerKey.equals("rawsuppliedname");
    }
    
    /**
     * Get the non-raw version of a taxonomic field name (case-insensitive)
     * e.g., "rawfamily" -> "family", "RawKingdom" -> "kingdom", "RAWORDER" -> "order", "rawSupplied_Name" -> "Supplied Name"
     * Removes prefix and preserves original case, converting underscores to spaces
     */
    private static String getWithoutRawPrefix(String key) {
        if (key != null && key.toLowerCase().startsWith("raw")) {
            return key.substring(3).replace('_', ' '); // Remove "raw" prefix, keep casing, replace underscore with space
        }
        return key != null ? key.replace('_', ' ') : null;
    }
    
    /**
     * Check if a list of kvps already contains a key (case-insensitive)
     */
    private static boolean containsKey(List<KvpValueVersion1> kvps, String key) {
        if (kvps == null || key == null) {
            return false;
        }
        return kvps.stream().anyMatch(kv -> key.equalsIgnoreCase(kv.getKey()));
    }
    
    /**
     * Build KVP values from properties, adding duplicate entries for raw taxonomic fields.
     * For migrated lists compatibility: if a property has key "rawFamily" (or other raw taxonomic fields),
     * we add both the raw version AND the non-raw version (e.g., both "rawFamily" and "family")
     * to maintain compatibility with the legacy system - but only if the non-raw version doesn't already exist.
     * 
     * @param properties The list of KeyValue properties from the SpeciesListItem
     * @return List of KvpValueVersion1 objects for the legacy API
     */
    private static List<KvpValueVersion1> buildKvpValues(List<au.org.ala.listsapi.model.KeyValue> properties) {
        List<KvpValueVersion1> kvps = new ArrayList<>();
        
        if (properties != null) {
            // First pass: add all properties with their legacy key names
            properties.forEach(kvpValue -> {
                String legacyKey = fixLegacyKeys(kvpValue.getKey());
                kvps.add(new KvpValueVersion1(legacyKey, kvpValue.getValue(), null));
            });
            
            // Second pass: for any key containing underscores, add a version with spaces
            properties.forEach(kvpValue -> {
                String originalKey = kvpValue.getKey();
                if (originalKey != null && originalKey.contains("_")) {
                    String spaceKey = originalKey.replace('_', ' ');
                    if (!containsKey(kvps, spaceKey)) {
                        kvps.add(new KvpValueVersion1(spaceKey, kvpValue.getValue(), null));
                    }
                }
            });

            // Third pass: for raw taxonomic fields, also add the non-raw version if it doesn't exist
            properties.forEach(kvpValue -> {
                String originalKey = kvpValue.getKey();
                if (isRawTaxonomicField(originalKey)) {
                    String nonRawKey = getWithoutRawPrefix(originalKey);
                    // Only add the non-raw version if it doesn't already exist
                    if (!containsKey(kvps, nonRawKey)) {
                        kvps.add(new KvpValueVersion1(nonRawKey, kvpValue.getValue(), null));
                    }
                }
            });
        }
        
        return kvps;
    }

    /**
     * Transforms a SpeciesListItem object to a QueryListItemVersion1 object
     *
     * @param speciesListItem The source SpeciesListItem object
     * @return A new QueryListItemVersion1 object populated with values from the source
     */
    public QueryListItemVersion1 transformToQueryListVersion1(SpeciesListItem speciesListItem) {
        if (speciesListItem == null) {
            return null;
        }

        QueryListItemVersion1 queryListItemV1 = new QueryListItemVersion1();
        String speciesListID = speciesListItem.getSpeciesListID();

        // Map properties from SpeciesList to SpeciesListVersion1
        queryListItemV1.setId(toLegacyId(speciesListItem));
        queryListItemV1.setSpeciesListID(speciesListID);
        queryListItemV1.setDataResourceUid(speciesListID); // fallback - attempt to set actual DataResourceUid via lookup, below
        queryListItemV1.setLsid(speciesListItem.getClassification() != null ? speciesListItem.getClassification().getTaxonConceptID() : null);
        queryListItemV1.setMatchedName(speciesListItem.getClassification() != null ? speciesListItem.getClassification().getScientificName() : null);
        queryListItemV1.setRawScientificName(speciesListItem.getScientificName());
        queryListItemV1.setCommonName(speciesListItem.getVernacularName() != null ? speciesListItem.getVernacularName() : speciesListItem.getClassification() != null ? speciesListItem.getClassification().getVernacularName() : null);

        // Get extra details via MongoDB lookup
        Optional<SpeciesList> speciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);

        List<KvpValueVersion1> kvps = buildKvpValues(speciesListItem.getProperties());

        queryListItemV1.setKvpValues(kvps);

        if (speciesList.isPresent()) {
            SpeciesList speciesListV2 = speciesList.get();
            queryListItemV1.setDataResourceUid(speciesListV2.getDataResourceUid() == null ? speciesListID : speciesListV2.getDataResourceUid());
            queryListItemV1.setName(speciesListV2.getTitle());
        } else {
            logger.warn("QueryListItemVersion1 transformToQueryListVersion1() -> Species list not found for ID: " + speciesListID);
        }

        return queryListItemV1;
    }

    /**
     * Transforms a SpeciesListItem to a SpeciesItemVersion1 object, suitable for the
     * /v1/species/** endpoint response. Performs a per-item MongoDB lookup.
     *
     * <p>Prefer {@link #transformToSpeciesItemVersion1(SpeciesListItem, Map)} when converting a
     * batch of items to avoid N+1 queries.
     *
     * @param speciesListItem The source SpeciesListItem object
     * @return A new SpeciesItemVersion1 populated with values from the source
     */
    public SpeciesItemVersion1 transformToSpeciesItemVersion1(SpeciesListItem speciesListItem) {
        if (speciesListItem == null) {
            return null;
        }
        String speciesListID = speciesListItem.getSpeciesListID();
        Optional<SpeciesList> speciesList =
                speciesListMongoRepository.findByIdOrDataResourceUid(speciesListID, speciesListID);
        Map<String, SpeciesList> listCache = speciesList
                .map(sl -> Map.of(speciesListID, sl))
                .orElse(Map.of());
        return transformToSpeciesItemVersion1(speciesListItem, listCache);
    }

    /**
     * Transforms a SpeciesListItem to a SpeciesItemVersion1 object using a pre-fetched map of
     * SpeciesList records, avoiding per-item MongoDB lookups.
     *
     * @param speciesListItem The source SpeciesListItem object
     * @param listCache Map keyed by speciesListID (or dataResourceUid) to SpeciesList, pre-fetched
     *     by the caller
     * @return A new SpeciesItemVersion1 populated with values from the source
     */
    public SpeciesItemVersion1 transformToSpeciesItemVersion1(
            SpeciesListItem speciesListItem, Map<String, SpeciesList> listCache) {
        if (speciesListItem == null) {
            return null;
        }

        SpeciesItemVersion1 item = new SpeciesItemVersion1();
        String speciesListID = speciesListItem.getSpeciesListID();

        item.setGuid(speciesListItem.getClassification() != null
                ? speciesListItem.getClassification().getTaxonConceptID() : null);
        item.setDataResourceUid(speciesListID);

        // Populate the abbreviated list details from the pre-fetched cache
        AbbrListVersion1 list = new AbbrListVersion1();
        SpeciesList sl = listCache.get(speciesListID);
        if (sl != null) {
            list.setListName(sl.getTitle());
            list.setUsername(sl.getOwner());
            list.setSds(sl.getIsSDS());
            list.setIsBIE(sl.getIsBIE());
            item.setDataResourceUid(sl.getDataResourceUid() != null ? sl.getDataResourceUid() : speciesListID);
        } else {
            logger.warn("SpeciesItemVersion1 transformToSpeciesItemVersion1() -> Species list not found for ID: " + speciesListID);
        }
        item.setList(list);

        // Build KVP values from properties
        List<KvpValueVersion1> kvps = buildKvpValues(speciesListItem.getProperties());
        item.setKvpValues(kvps);

        return item;
    }
}
