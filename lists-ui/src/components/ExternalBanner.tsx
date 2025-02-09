'use client';

import { Flex, Stack, Text, useMantineTheme } from '@mantine/core';
import { useMounted } from '@mantine/hooks';
import { useEffect, useMemo, useState } from 'react';
import {
  StopIcon,
  InfoIcon,
  CautionIcon,
  mainShades,
} from '@atlasoflivingaustralia/ala-mantine';

interface Message {
  message: string;
  severity: 'INFO' | 'WARNING' | 'DANGER';
  updated: string;
}

type Messages = {
  [key: string]: Message;
};

interface ExternalBannerProps {
  url?: string;
  services?: string[];
}

function ExternalBanner({ url, services }: ExternalBannerProps) {
  const [rawMessages, setRawMessages] = useState<Messages | null>(null);
  const theme = useMantineTheme();
  const mounted = useMounted();

  const styles = useMemo(
    () => ({
      INFO: {
        bg: theme.colors['lagoon'][mainShades['lagoon']],
        fg: 'white',
        icon: InfoIcon,
      },
      WARNING: {
        bg: theme.colors['honey'][mainShades['honey']],
        fg: theme.colors['charcoal'][mainShades['charcoal']],
        icon: CautionIcon,
      },
      DANGER: {
        bg: theme.colors['pinot'][mainShades['pinot']],
        fg: 'white',
        icon: StopIcon,
      },
    }),
    [theme]
  );

  // Only show global & user-defined messages
  const messages = useMemo<Message[] | null>(
    () =>
      rawMessages
        ? Object.entries(rawMessages)
            .filter(
              ([service, { message }]) =>
                ['global', ...(services || [])].includes(service) &&
                message.length > 0
            )
            .map(([_, message]) => message)
        : null,
    [rawMessages, services]
  );

  // Effect hook for fetching messages
  useEffect(() => {
    async function fetchMessages() {
      try {
        const req = await fetch(url || '');

        // Ensure the request was okay
        if (req.ok) {
          setRawMessages(await req.json());
        } else {
          throw new Error(`ALA banner request returned ${req.status}`);
        }
      } catch (error) {
        console.error('Error fetching ALA banner messages', error);
      }
    }

    if (mounted && url) fetchMessages();
  }, [mounted, url]);

  return messages ? (
    <Stack gap={0}>
      {messages.map(({ message, severity }) => {
        const Icon = styles[severity].icon;
        return (
          <Flex
            bg={styles[severity].bg}
            align='center'
            justify='center'
            p={10}
            gap={10}
          >
            {
              <Icon
                color={styles[severity].fg}
                style={{ minWidth: 14, minHeight: 14 }}
              />
            }
            <Text
              c={styles[severity].fg}
              dangerouslySetInnerHTML={{ __html: message }}
            />
          </Flex>
        );
      })}
    </Stack>
  ) : null;
}

export { ExternalBanner };
