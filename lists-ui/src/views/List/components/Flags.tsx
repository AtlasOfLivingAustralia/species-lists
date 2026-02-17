/* eslint-disable @typescript-eslint/no-explicit-any */
import { SpeciesList } from '#/api';
import { ListTypeBadge } from '#/components/ListTypeBadge';
import { listFlags } from '#/helpers';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Box, Group, Paper, PaperProps, Text, Tooltip } from '@mantine/core';
import { FormattedMessage, useIntl } from 'react-intl';

interface FlagsProps extends PaperProps {
  meta: SpeciesList;
}

export function Flags({ meta, ...rest }: FlagsProps) {
  const intl = useIntl();

  return (
    <Paper {...rest}  p={0} radius='lg' mt={5} mb={5}>
      <Group gap='xs'>
        <Tooltip
          position='bottom'
          disabled={!meta.lastUpdated}
          label={ intl.formatMessage({ id: 'list.is.tooltip', defaultMessage: 'List visibility' }) }
          withArrow
        >
          <Box style={{ display: 'flex', alignItems: 'center', overflow: 'hidden', whiteSpace: 'nowrap', maxWidth: '100%' }}>
            <FontAwesomeIcon fontSize={15} color='grey' icon={meta?.isPrivate ? faEyeSlash : faEye} />
            <Text size='sm' fw='400' ml={6}>
            {meta?.isPrivate ? (
              <FormattedMessage id="access.private" defaultMessage="Private" />
            ) : (
              <FormattedMessage id="access.public" defaultMessage="Public" />
            )}
            </Text>
          </Box>
        </Tooltip>
        {listFlags.map(({ flag }) =>
          (meta as any)[flag] ? (
            <ListTypeBadge listTypeValue={flag} key={flag} />
          ) : null
        )}
      </Group>
    </Paper>
  );
}
