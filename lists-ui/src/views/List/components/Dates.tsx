/* eslint-disable @typescript-eslint/no-explicit-any */
import { SpeciesList } from '#/api';
import { faCalendar } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  PaperProps,
  Text,
  Tooltip,
  useMantineTheme
} from '@mantine/core';
import { useIntl } from 'react-intl';

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
  const intl = useIntl();

  return (
    <Text size='sm' opacity={1} pl={6}>
      <Tooltip
        position='bottom'
        disabled={!meta.lastUpdated}
        style={{ fontSize: theme.fontSizes.sm }}
        label={ intl.formatMessage({ id: 'list.dates.tooltip', defaultMessage: 'List content last updated' }) }
        withArrow
      >
        <span>
          <FontAwesomeIcon style={{ marginRight: 6 }} icon={faCalendar} color='grey' />
          {intl.formatMessage({ id: 'list.lastUpdated.label', defaultMessage: 'List' })}:{' '}
          {formatDateString(meta.lastUpdated)}{' '}
        </span>
      </Tooltip>
      <Tooltip
        position='bottom'
        disabled={!meta.metadataLastUpdated}
        style={{ fontSize: theme.fontSizes.sm }}
        label={ intl.formatMessage({ id: 'list.metadata.tooltip', defaultMessage: 'List metadata last updated' }) }
        withArrow
      >
        <span>
          <FontAwesomeIcon style={{ marginRight: 6, marginLeft: 8 }} icon={faCalendar} color='grey' />
          {intl.formatMessage({ id: 'list.metadataLastUpdated.label', defaultMessage: 'Metadata' })}:{' '}
          {formatDateString(meta.metadataLastUpdated)}
        </span>
        </Tooltip>
    </Text>
  );
}
