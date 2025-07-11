/* eslint-disable @typescript-eslint/no-explicit-any */
import { SpeciesList } from '#/api';
import { faCalendar } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Group,
  Paper,
  PaperProps,
  Text,
  Tooltip,
  useMantineTheme,
} from '@mantine/core';

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
  const theme = useMantineTheme();

  return (
    <Tooltip
      position='top'
      disabled={!meta.metadataLastUpdated}
      style={{ fontSize: theme.fontSizes.sm }}
      label={
        <>
          <b>Metadata</b>{' '}
          {meta.metadataLastUpdated
            ? formatDateString(meta.metadataLastUpdated)
            : '?'}
        </>
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
