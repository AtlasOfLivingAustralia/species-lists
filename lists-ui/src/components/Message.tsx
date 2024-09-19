import { MouseEventHandler, ReactNode } from 'react';
import { Anchor, Button, Center, Stack, Text, ThemeIcon } from '@mantine/core';
import { ListIcon } from '@atlasoflivingaustralia/ala-mantine';

interface MessageProps {
  title?: string;
  subtitle?: string;
  action?: string;
  onAction?: MouseEventHandler<HTMLButtonElement>;
  icon?: ReactNode;
  withAlaContact?: boolean;
}

export function Message({
  title,
  subtitle,
  action,
  onAction,
  icon,
  withAlaContact,
}: MessageProps) {
  return (
    <Center mih={250} style={{ textAlign: 'center' }}>
      <Stack align='center' gap='xs'>
        <ThemeIcon variant='light' size='xl' radius='xl' mb='md'>
          {icon ? icon : <ListIcon size={18} />}
        </ThemeIcon>
        <Stack align='center' gap='sm'>
          <Text size='xl' fw='bold'>
            {title || 'No results found'}
          </Text>
          <Stack gap={6} align='center'>
            <Text size='sm' c='dimmed'>
              {subtitle || 'Try refining your search'}
            </Text>
            {withAlaContact && (
              <Text size='sm' c='dimmed'>
                If this issue persists, contact us at{' '}
                <Anchor size='sm' href='mailto:support@ala.org.au'>
                  support@ala.org.au
                </Anchor>
              </Text>
            )}
          </Stack>
          {action && (
            <Button
              mt='lg'
              radius='md'
              size='sm'
              variant='contained'
              onClick={onAction}
            >
              {action}
            </Button>
          )}
        </Stack>
      </Stack>
    </Center>
  );
}
