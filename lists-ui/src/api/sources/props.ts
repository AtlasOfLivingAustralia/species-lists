import {Facet, SpeciesList, SpeciesListItem} from "./model.ts";

interface SpeciesListSideBarProps {
    selectedView: string;
    resetSpeciesList: () => void | null | undefined;
}

interface FacetProps {
    facet: Facet;
    hideCount?: boolean;
    addToQuery: (facetName: string, facetValue: string) => void;
}

interface SpeciesListProps {
    setSpeciesList: (speciesList: SpeciesList | null | undefined) => void;
    resetSpeciesList: () => void;
}

interface SpeciesListItemProps {
    loading: boolean;
    speciesListID?: string;
    speciesListItemID?: string;
    selectedItem: SpeciesListItem;
    customFields?: string[];
    currentFilters?: any[];
    setIsEditing?: (isEditing: boolean) => void;
    resetSelectedIndex?: () => void;
}

interface MetadataFormProps {
    speciesList: SpeciesList;
    suppliedFields: string[];
    submitFormFcn: (speciesList:SpeciesList) => void;
    formButtons: React.ReactNode;
    resetUpload: () => void;
    edit: boolean;
}

interface TaxonImageProps {
    taxonID: string;
}

interface MetadataProps {
    setSpeciesList: (data: any) => void;
}

export type {MetadataProps, TaxonImageProps, MetadataFormProps, SpeciesListItemProps, SpeciesListProps, FacetProps, SpeciesListSideBarProps};