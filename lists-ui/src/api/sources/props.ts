import {Facet, SpeciesList, SpeciesListItem} from "./model.ts";

interface SpeciesListSideBarProps {
    selectedView: string;
    resetSpeciesList: () => void | null | undefined;
}

interface FacetProps {
    facet: Facet;
    addToQuery: (facetName: string, facetValue: string) => void;
}

interface SpeciesListProps {
    setSpeciesList: (speciesList: SpeciesList | null | undefined) => void;
    resetSpeciesList: () => void;
}

interface SpeciesListItemProps {
    loading: boolean;
    selectedItem: SpeciesListItem;
    customFields?: string[];
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