import {Button} from "@mantine/core";
import {IconSquareRoundedXFilled} from "@tabler/icons-react";
import {FormattedMessage} from "react-intl";

interface SelectedFacetProps {
    facet: { key: string; value: string };
    idx: number;
    removeFacet: (idx: number) => void;
}

export function SelectedFacet({ facet, idx, removeFacet }: SelectedFacetProps) {
    return (
        <Button variant={"outline"} onClick={() => removeFacet(idx)} style={{ marginBottom: '5px'}}>
            <IconSquareRoundedXFilled />
            <FormattedMessage id={facet.key} /> : <FormattedMessage id={facet.value || 'not.supplied'} defaultMessage={facet.value || 'Not supplied'} />
        </Button>
    );
}