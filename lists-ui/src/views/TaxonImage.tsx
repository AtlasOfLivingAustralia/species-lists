import {gql, useQuery} from "@apollo/client";
import {Image, Skeleton, Space} from "@mantine/core";
import {TaxonImageProps} from "../api/sources/props.ts";

export function TaxonImage({ taxonID }: TaxonImageProps) {
    const GET_IMAGE = gql`
        query loadImage($taxonID: String!) {
            getTaxonImage(taxonID: $taxonID) {
                url
            }
        }
    `;

    const { loading, error, data } = useQuery<{ getTaxonImage: { url: string | null } }>(GET_IMAGE, {
        variables: {
            taxonID: taxonID,
        },
    });

    if (loading) return <>
        <Skeleton width={300} height={200}>
            Loading
        </Skeleton>
        <Space h="md" />
    </>;

    if (error) {
        console.log('Error loading image for ' + taxonID);
        return null;
    }

    if (!data?.getTaxonImage?.url) {
        return  <><Skeleton width={300} height={200} visible={true}>
            No Image available
        </Skeleton>
            <Space h="md" /></>;
    }

    return (
        <>
            <div>
                <Image width={300} height={200} radius="sm" src={data?.getTaxonImage?.url} alt="Taxon image" />
                <Space h="md" />
            </div>
        </>
    );
}