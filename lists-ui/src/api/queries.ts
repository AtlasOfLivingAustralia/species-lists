export const QUERY_LISTS_SEARCH = `query findList($searchQuery: String, $page: Int, $size: Int, $isPrivate: Boolean, $filters: [Filter], $userId: String) {
  lists(
    searchQuery: $searchQuery
    page: $page
    size: $size
    isPrivate: $isPrivate
    filters: $filters
    userId: $userId
  ) {
    content {
      id
      title
      rowCount
      listType
      isPrivate
      __typename
    }
    totalPages
    totalElements
    __typename
  }
  facets: facetSpeciesLists(searchQuery: $searchQuery, isPrivate: $isPrivate, userId: $userId) {
    key
    counts {
      value
      count
      __typename
    }
    __typename
  }
}`;

export const QUERY_LISTS_GET = `query loadList(
  $speciesListID: String!
  $searchQuery: String
  $filters: [Filter]
  $page: Int
  $size: Int
  $sort: String
  $dir: String
) {
  meta: getSpeciesListMetadata(speciesListID: $speciesListID) {
    id
    title
    description
    rowCount
    distinctMatchCount
    fieldList
    owner
    ownerName
    authority
    editors
    tags
    region
    licence
    listType
    isAuthoritative
    isInvasive
    isPrivate
    isBIE
    isSDS
    isThreatened
  }
  list: filterSpeciesList(
    speciesListID: $speciesListID
    searchQuery: $searchQuery
    page: $page
    size: $size
    filters: $filters
    sort: $sort
    dir: $dir
  ) {
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
  facets: facetSpeciesList(
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
}`;

export const MUTATION_LIST_UPDATE = `mutation update(
  $id: String!
  $title: String!
  $description: String
  $licence: String!
  $listType: String!
  $authority: String
  $region: String
  $isAuthoritative: Boolean
  $isPrivate: Boolean
  $isThreatened: Boolean
  $isInvasive: Boolean
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
    isThreatened: $isThreatened
    isInvasive: $isInvasive
    isSDS: $isSDS
    isBIE: $isBIE
    wkt: $wkt
    tags: $tags
  ) {
    id
  }
}`;

export const QUERY_IMAGE_GET = `query loadImage($taxonID: String!) {
  image: getTaxonImage(taxonID: $taxonID) {
    url
    __typename
  }
}`;

export const MUTATION_LIST_ITEM_UPDATE = `mutation update($editItem: InputSpeciesListItem) {
  newItem: updateSpeciesListItem(inputSpeciesListItem: $editItem) {
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
}`;

export const MUTATION_LIST_ITEM_DELETE = `mutation delete($id: String!) {
  removeSpeciesListItem(id: $id) {
    id
  }
}`;

export const MUTATION_LIST_FIELD_CREATE = `mutation addField(
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
  }
}`;

export const MUTATION_LIST_FIELD_DELETE = `mutation removeField(
    $id: String!
    $field: String!
  ) {
    removeField(
      id: $id
      fieldName: $field
    ) {
    id
  }
}`;

export const MUTATION_LIST_FIELD_RENAME = `mutation renameField(
    $id: String!
    $field: String!
    $updatedField: String!
  ) {
    renameField(
      id: $id
      oldName: $field
      newName: $updatedField
    ) {
      id
    }
  }
`;
