import {User} from "oidc-client-ts";

interface SpeciesList {
    authority: string;
    createDate: string;
    description: string;
    doi: string;
    facetList: string[];
    fieldList: string[];
    id: string;
    isAuthoritative: boolean;
    isInvasive: boolean;
    isPrivate: boolean;
    isBIE: boolean;
    isSDS: boolean;
    isThreatened: boolean;
    lastModifiedDate: string;
    licence: string;
    listType: string;
    region: string;
    rowCount: number;
    title: string;
    lastUpdatedBy: string;
    owner: string;
    wkt: string;
}

type Release = {
    releasedVersion: string;
    createdDate: string;
    metadata: {
        fieldList: string[];
        rowCount: number;
    };
};

type ReleasesData = {
    getSpeciesListMetadata: SpeciesList;
    listReleases: Release[];
};

interface Breadcrumb {
    title: string;
    href: string | undefined | null ;
}

interface FacetCount {
    value: string;
    count: number;
}

interface Facet {
    key: string;
    counts: FacetCount[];
}

interface SpeciesListItem {

    id: string;
    scientificName: string;
    vernacularName: string;
    kingdom: string;
    phylum: string;
    classs: string;
    order: string;
    family: string;
    genus: string;
    properties: KV[];
    classification: Classification;
}

interface KV {
    key: string;
    value: string;
}

interface Classification {
    scientificName: string;
    vernacularName: string;
    taxonConceptID: string;
    kingdom: string;
    phylum: string;
    classs: string;
    order: string;
    family: string;
    genus: string;
    kingdomID: string;
    phylumID: string;
    classID: string;
    orderID: string;
    familyID: string;
    genusID: string;
}

interface ListsUser {
    user: User,
    userId: string,
    isAdmin: boolean,
    roles: string[]
}

interface FilteredSpeciesList {
    content: SpeciesListItem[];
    totalPages: number;
    totalElements: number;
}

export type {Breadcrumb,  SpeciesList, Facet, FacetCount, FilteredSpeciesList, SpeciesListItem, Release, ReleasesData, ListsUser, Classification, KV}