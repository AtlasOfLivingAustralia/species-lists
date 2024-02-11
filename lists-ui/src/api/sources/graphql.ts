import {gql} from "@apollo/client";

export const GET_LIST = gql`
    query loadList($speciesListID: String!, $searchQuery:String, $filters:[Filter], $page: Int, $size: Int) {
        getSpeciesListMetadata(speciesListID: $speciesListID) {
            id
            title
            rowCount
            fieldList
            owner
            editors
        }
        filterSpeciesList(speciesListID: $speciesListID, searchQuery: $searchQuery, page: $page, size: $size, filters: $filters){
            content {
                id
                scientificName
                vernacularName
                genus
                family
                classs
                order
                phylum
                kingdom
                properties {
                    key
                    value
                }
                classification {
                    scientificName
                    scientificNameAuthorship
                    vernacularName
                    taxonConceptID
                    kingdom
                    phylum
                    classs
                    order
                    family
                    genus
                    kingdomID
                    phylumID
                    classID
                    orderID
                    familyID
                    genusID
                }
                dateCreated
                lastUpdated
                lastUpdatedBy
            }
            totalPages
            totalElements
        }
        facetSpeciesList(
            speciesListID: $speciesListID
            searchQuery: $searchQuery
            filters: $filters
            facetFields: []
        ) {
            key
            counts {
                value
                count
            }
        }
    }
`;

export const SEARCH_SPECIES_LISTS = gql`
    query findList($searchQuery: String, $page: Int, $size: Int, $isPrivate: Boolean, $filters:[Filter]) {
        lists(searchQuery: $searchQuery, page: $page, size: $size, isPrivate: $isPrivate, filters:$filters) {
            content {
                id
                title
                rowCount
                listType
            }
            totalPages
            totalElements
        }
        facetSpeciesLists (searchQuery: $searchQuery, isPrivate: $isPrivate) {
            key
            counts {
                value
                count
            }
        }        
    }
`;

export const SEARCH_PRIVATE_SPECIES_LISTS = gql`
    query findList($searchQuery: String, $page: Int, $size: Int, $isPrivate: Boolean) {
        lists(searchQuery: $searchQuery, page: $page, size: $size, isPrivate: $isPrivate) {
            content {
                id
                title
                rowCount
                listType
            }
            totalPages
            totalElements
        }
        facetSpeciesLists (searchQuery: $searchQuery, isPrivate: $isPrivate) {
            key
            counts {
                value
                count
            }
        }
    }
`;


export const UPDATE_SPECIES_LIST_ITEM = gql`
    mutation update(
        $inputSpeciesListItem: InputSpeciesListItem
    ) {
        updateSpeciesListItem(
            inputSpeciesListItem: $inputSpeciesListItem
        ) {
            id
            scientificName
            vernacularName
            genus
            family
            classs
            order
            phylum
            kingdom
            properties {
                key
                value
            }
            classification {
                scientificNameAuthorship
                scientificName
                kingdom
                phylum
                classs
                order
                family
                genus
                taxonConceptID
            }
            dateCreated
            lastUpdated
            lastUpdatedBy            
        }
    }
`;

export const DELETE_SPECIES_LIST_ITEM = gql`
    mutation delete(
        $id: String!
    ) {
        removeSpeciesListItem(
            id: $id
        ) {
            id
        }
    }
`;

export const GET_RELEASES = gql`
    query loadList($speciesListID: String!) {
        getSpeciesListMetadata(speciesListID: $speciesListID) {
            id
            title
            licence
            rowCount
            fieldList
            listType
            doi
            authority
            region
            isAuthoritative
            isPrivate
            isInvasive
            isThreatened
        }
        listReleases(speciesListID: $speciesListID) {
            releasedVersion
            metadata {
                fieldList
                rowCount
            }
            createdDate
        }
    }
`;

export const GET_MY_LISTS = gql`
    query findList($searchQuery: String, $page: Int, $size: Int, $userId: String) {
        lists(searchQuery: $searchQuery, page: $page, size: $size, userId: $userId) {
            content {
                id
                title
                rowCount
                listType
                isAuthoritative
                isPrivate
            }
            totalPages
            totalElements
        }
    }
`;

export const GET_LIST_METADATA = gql`
    query loadList($speciesListID: String!) {
        getSpeciesListMetadata(speciesListID: $speciesListID) {
            id
            title
            description
            licence
            rowCount
            fieldList
            listType
            doi
            authority
            region
            isAuthoritative
            isPrivate
            isInvasive
            isThreatened
            isBIE
            isSDS
            dateCreated
            lastUpdated
            lastUploaded
            lastUpdatedBy
            owner
            editors
            wkt
            tags
        }
    }
`;

export const UPDATE_LIST = gql`
    mutation update(
        $id: String!
        $title: String!
        $description: String
        $licence: String!
        $listType: String!
        $authority: String
        $region: String
        $isAuthoritative: Boolean
        $isPrivate: Boolean
        $isSDS: Boolean
        $isBIE: Boolean
        $wkt: String
        $tags: [String]
    ) {
        updateMetadata(
            id: $id
            title: $title
            description: $description
            licence: $licence
            listType: $listType
            authority: $authority
            region: $region
            isAuthoritative: $isAuthoritative
            isPrivate: $isPrivate
            isSDS: $isSDS
            isBIE: $isBIE
            wkt: $wkt
            tags: $tags
        ) {
            id
            title
            description
            licence
            rowCount
            fieldList
            listType
            doi
            authority
            region
            isAuthoritative
            isPrivate
            isInvasive
            isThreatened
            isSDS
            isBIE
            dateCreated
            lastUpdated
            lastUploaded
            owner
            editors
            wkt
            tags
        }
    }
`;

export const REMOVE_FIELD = gql`
    mutation removeField(
        $id: String!
        $fieldName: String!
    ) {
        removeField(
            id: $id
            fieldName: $fieldName
        ) {
            id
            title
            description
            licence
            rowCount
            fieldList
            listType
            doi
            authority
            region
            isAuthoritative
            isPrivate
            isInvasive
            isThreatened
            isSDS
            isBIE
            dateCreated
            lastUpdated
            lastUploaded
            owner
            editors
            wkt
        }
    }
`;

export const RENAME_FIELD = gql`
    mutation renameField(
        $id: String!
        $oldName: String!
        $newName: String!
    ) {
        renameField(
            id: $id
            oldName: $oldName
            newName: $newName
        ) {
            id
            title
            description
            licence
            rowCount
            fieldList
            listType
            doi
            authority
            region
            isAuthoritative
            isPrivate
            isInvasive
            isThreatened
            isSDS
            isBIE
            dateCreated
            lastUpdated
            lastUploaded
            owner
            editors
            wkt
        }
    }
`;

export const ADD_FIELD = gql`
    mutation addField(
        $id: String!
        $fieldName: String!,
        $fieldValue: String
    ) {
        addField(
            id: $id
            fieldName: $fieldName
            fieldValue: $fieldValue
        ) {
            id
            title
            description
            licence
            rowCount
            fieldList
            listType
            doi
            authority
            region
            isAuthoritative
            isPrivate
            isInvasive
            isThreatened
            isSDS
            isBIE
            dateCreated
            lastUpdated
            lastUploaded
            owner
            editors
            wkt
        }
    }
`;
