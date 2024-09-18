/* eslint-disable @typescript-eslint/no-explicit-any */
import { Group, Text, Paper, PaperProps } from '@mantine/core';
import { SpeciesList } from '#/api';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { listFlags } from '#/helpers';

interface FlagsProps extends PaperProps {
  meta: SpeciesList;
}

export function Flags({ meta, ...rest }: FlagsProps) {
  return (
    <Paper {...rest} withBorder p='xs' radius='md'>
      <Group gap='xs'>
        <Text size='xs' fw='bold' opacity={0.75}>
          <FontAwesomeIcon
            style={{ marginRight: 10 }}
            icon={meta.isPrivate ? faEyeSlash : faEye}
          />
          {meta.isPrivate ? 'Private' : 'Public'}
        </Text>
        {listFlags.map(({ flag, label, icon }) =>
          (meta as any)[flag] ? (
            <Text key={flag} size='xs' fw='bold' opacity={0.75}>
              <FontAwesomeIcon style={{ marginRight: 10 }} icon={icon} />
              {label}
            </Text>
          ) : null
        )}
      </Group>
    </Paper>
  );
}
