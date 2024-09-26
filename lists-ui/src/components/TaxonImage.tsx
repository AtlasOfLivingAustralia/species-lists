import { useEffect, useState } from 'react';
import { Image, ImageProps, Center, Loader } from '@mantine/core';
import { performGQLQuery, queries } from '#/api';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faImage } from '@fortawesome/free-regular-svg-icons';
import { useMounted } from '@mantine/hooks';

interface TaxonImageProps extends ImageProps {
  taxonID: string;
}

export function TaxonImage({ taxonID, ...rest }: TaxonImageProps) {
  const [url, setUrl] = useState<string | null>(null);
  const [loaded, setLoaded] = useState<boolean>(!taxonID);
  const mounted = useMounted();

  // Fetch the image from the GraphQL service
  useEffect(() => {
    async function runQuery() {
      try {
        const { image } = await performGQLQuery(queries.QUERY_IMAGE_GET, {
          taxonID,
        });

        if (image?.url) {
          setUrl(image?.url);
        } else {
          setLoaded(true);
        }
      } catch (error) {
        setLoaded(true);
      }
    }

    if (mounted && taxonID) runQuery();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mounted]);

  return (
    <>
      {!url && (
        <Center w={rest.w} h={rest.h}>
          {loaded ? (
            <FontAwesomeIcon icon={faImage} fontSize={32} color='grey' />
          ) : (
            <Loader />
          )}
        </Center>
      )}
      {url && (
        <Image
          {...rest}
          onLoad={() => setLoaded(true)}
          src={url}
          radius={0}
          alt='Image of taxon'
        />
      )}
    </>
  );
}
