/* eslint-disable @typescript-eslint/no-explicit-any */
import { Group, Text, Paper, PaperProps, Tooltip } from '@mantine/core';
import { SpeciesList } from '#/api';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCalendar } from '@fortawesome/free-solid-svg-icons';
import { useColorScheme } from '@mantine/hooks';

interface DatesProps extends PaperProps {
  meta: SpeciesList;
}

const formatDateString = (inputDate: string) => {
  const parts = inputDate.split(' ');
  parts.splice(3, 2);
  const date = new Date(parts.join(' '));

  return `${date.getDate()}/${date.getMonth() + 1}/${date.getFullYear()}`;
};

export function Dates({ meta, ...rest }: DatesProps) {
  const colorScheme = useColorScheme();

  return (
    <Tooltip
      position='top'
      disabled={!meta.metadataLastUpdated}
      label={
        <Text size='xs' c={colorScheme === 'dark' ? 'white' : 'black'}>
          <b>Metadata</b>{' '}
          {meta.metadataLastUpdated
            ? formatDateString(meta.metadataLastUpdated)
            : '?'}
        </Text>
      }
      withArrow
    >
      <Paper {...rest} withBorder p='xs' radius='md'>
        <Group gap='xs'>
          <Text size='xs' opacity={0.75}>
            <FontAwesomeIcon style={{ marginRight: 10 }} icon={faCalendar} />
            <b>Updated</b> {formatDateString(meta.lastUpdated)}
          </Text>
        </Group>
      </Paper>
    </Tooltip>
  );
}
