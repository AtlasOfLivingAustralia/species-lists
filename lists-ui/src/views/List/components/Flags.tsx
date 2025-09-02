/* eslint-disable @typescript-eslint/no-explicit-any */
import { SpeciesList } from '#/api';
import { ListTypeBadge } from '#/components/ListTypeBadge';
import { listFlags } from '#/helpers';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Box, Group, Paper, PaperProps, Text } from '@mantine/core';
import { FormattedMessage } from 'react-intl';

interface FlagsProps extends PaperProps {
  meta: SpeciesList;
}

export function Flags({ meta, ...rest }: FlagsProps) {
  return (
    <Paper {...rest}  p={0} radius='lg' mt={5} mb={5}>
      <Group gap='xs'>
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
        {listFlags.map(({ flag }) =>
          (meta as any)[flag] ? (
            <ListTypeBadge listTypeValue={flag}/>
          ) : null
        )}
      </Group>
    </Paper>
  );
}
