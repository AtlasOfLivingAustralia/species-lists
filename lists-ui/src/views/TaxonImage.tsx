import {gql, useQuery} from "@apollo/client";
import { Image, Skeleton, Space} from "@mantine/core";
import {TaxonImageProps} from "../api/sources/props.ts";

interface ImageResult {
    url: string;
}

export function TaxonImages({ taxonID }: TaxonImageProps) {

    const GET_IMAGES = gql`
        query loadImages($taxonID: String!, $size: Int, $page: Int) {
            getTaxonImages(taxonID: $taxonID, size: $size, page: $page) {
                url
            }
        }
    `;

    const { loading, error, data } =
        useQuery<{
            getTaxonImages: Array<ImageResult>
        }>(GET_IMAGES, {
        variables: {
            taxonID: taxonID,
            size: 3,
            page: 0,
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

    if (!data || data.getTaxonImages.length === 0) {
        return  <Image width={300} height={200} radius="sm" src={null}
                                      alt="Taxon image"
                                      withPlaceholder={true}
        />;
    }

    const results: Array<ImageResult> = data.getTaxonImages;

    return (
        <>
            { results?.map((result) => (
                <Image width={`auto`} height={200} radius="sm" src={result.url}
                        alt="Taxon image"
                        withPlaceholder={true}
                />
            ))}
        </>
    );
}


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

    const url = data?.getTaxonImage?.url;

    return (
        <>
            <div>
                <Image  width={300} height={200} radius="sm" src={url}
                       alt="Taxon image"
                       withPlaceholder={true}
                  />
                <Space h="md" />
            </div>
        </>
    );
}