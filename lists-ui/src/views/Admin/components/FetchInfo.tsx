import { useEffect, useState } from 'react';
import { Code, LoadingOverlay } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { getErrorMessage } from '#/helpers';
import { useMounted } from '@mantine/hooks';

interface FetchInfoProps {
  fetcher?: () => Promise<unknown>;
}

export function FetchInfo({ fetcher }: FetchInfoProps) {
  const [info, setInfo] = useState<unknown | null>(null);
  const mounted = useMounted();

  useEffect(() => {
    async function fetchInfo() {
      try {
        setInfo(await fetcher!());
      } catch (error) {
        console.log('admin error', error);
        // Show error notification
        notifications.show({
          message: getErrorMessage(error),
          position: 'bottom-left',
          radius: 'md',
        });
      }
    }

    if (mounted && fetcher) fetchInfo();
  }, [mounted, fetcher]);

  return (
    <>
      <LoadingOverlay visible={!Boolean(info)} />
      <Code h={500} block>
        {info ? JSON.stringify(info, null, 2) : ''}
      </Code>
    </>
  );
}
