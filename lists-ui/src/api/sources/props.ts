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
    speciesList: {
        id: string;
        title: string;
        description: string;
        listType: string;
        licence: string;
        authority: string;
        region: string;
        isPrivate: boolean;
        isAuthoritative: boolean;
        isSDS: boolean;
        isBIE: boolean;
        isThreatened: boolean;
        isInvasive: boolean;
    };
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