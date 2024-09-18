import { User } from 'oidc-client-ts';

interface SpeciesListPage {
  content: SpeciesList[];
  currentPage: number;
  totalElements: number;
  totalPages: number;
}

interface SpeciesList {
  authority: string;
  dateCreated: string;
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
  lastUpdated: string;
  licence: string;
  listType: string;
  region: string;
  rowCount: number;
  title: string;
  lastUpdatedBy: string;
  owner: string;
  wkt: string;
  tags: string[];
}

type SpeciesListSubmit = Omit<
  SpeciesList,
  | 'dateCreated'
  | 'doi'
  | 'facetList'
  | 'fieldList'
  | 'id'
  | 'rowCount'
  | 'lastUpdatedBy'
  | 'owner'
>;

interface Release {
  releasedVersion: string;
  createdDate: string;
  metadata: {
    fieldList: string[];
    rowCount: number;
  };
}

interface ReleasesData {
  getSpeciesListMetadata: SpeciesList;
  listReleases: Release[];
}

interface UploadResult {
  facetList: string[];
  fieldList: string[];
  localFile: string;
  originalFieldNames: string[];
  rowCount: number;
  validationErrors: string[];
}

interface Breadcrumb {
  title: string;
  href: string | undefined | null;
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
  lastUpdated: string;
  lastUpdatedBy: string;
  dateCreated: string;
}

interface InputSpeciesList {
  id: string;
  speciesListID: string;
  scientificName: string;
  vernacularName: string;
  kingdom: string;
  phylum: string;
  classs: string;
  order: string;
  family: string;
  genus: string;
  properties: InputKeyValue[];
}

interface InputKeyValue {
  key: string;
  value: string;
}

interface KV {
  key: string;
  value: string;
}

interface Classification {
  scientificName: string;
  scientificNameAuthorship: string;
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
  user: User;
  userId: string;
  isAdmin: boolean;
  roles: string[];
}

interface FilteredSpeciesList {
  content: SpeciesListItem[];
  totalPages: number;
  totalElements: number;
}

interface Constraint {
  value: string;
  label: string;
}

interface SpeciesListConstraints {
  licenses: Constraint[];
  lists: Constraint[];
  countries: Constraint[];
}

export type {
  Breadcrumb,
  UploadResult,
  InputSpeciesList,
  SpeciesList,
  SpeciesListSubmit,
  SpeciesListPage,
  Facet,
  FacetCount,
  FilteredSpeciesList,
  SpeciesListItem,
  SpeciesListConstraints,
  Constraint,
  Release,
  ReleasesData,
  ListsUser,
  Classification,
  KV,
};
