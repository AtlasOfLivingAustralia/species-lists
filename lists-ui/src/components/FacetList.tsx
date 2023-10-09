import {FacetProps} from "../api/sources/props.ts";
import {List, ScrollArea, Text, Title} from "@mantine/core";
import {FormattedMessage, FormattedNumber} from "react-intl";
import {FacetCount} from "../api/sources/model.ts";

export function FacetList({ facet, addToQuery, hideCount }: FacetProps) {
    if (facet.counts.length === 0) return null;

    return (
        <>
            <Title order={6}>
                <FormattedMessage id={facet.key} />
            </Title>
            <ScrollArea.Autosize mah={250}  mx="auto">
                <List>
                    {facet.counts.map((facetCount: FacetCount) => (
                        <List.Item onClick={() => addToQuery(facet.key, facetCount.value)}>
                            <Text fz="sm" style={{ color: '#c44d34', cursor: 'pointer' }}>
                                <FormattedMessage id={facetCount.value || 'not.supplied'} defaultMessage={facetCount.value || 'Not supplied'} />
                                {!hideCount && <> (<FormattedNumber value={facetCount.count} />)
                                </>}
                            </Text>
                        </List.Item>
                    ))}
                </List>
            </ScrollArea.Autosize>
        </>
    );
}