import { Anchor, Group, Skeleton, Stack, Table, Text } from '@mantine/core';
import { FormattedMessage, FormattedNumber } from 'react-intl';
import { FolderIcon } from '@atlasoflivingaustralia/ala-mantine';
import { SpeciesList } from '#/api';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEye, faEyeSlash } from '@fortawesome/free-regular-svg-icons';
import { Link } from 'react-router-dom';

interface ListRowProps {
  list?: SpeciesList;
}

export function ListRow({ list }: ListRowProps) {
  const loading = Boolean(list);

  return (
    <Table.Tr>
      <Table.Td>
        <Stack gap={4} pb='md'>
          <Skeleton visible={!loading}>
            <Anchor size='md' fw={500} component={Link} to={`list/${list?.id}`}>
              {list?.title || 'List title'}
            </Anchor>
          </Skeleton>
          <Skeleton w='50%' visible={!loading}>
            <Text size='sm'>
              <FormattedMessage id={list?.listType || 'OTHER'} />
            </Text>
          </Skeleton>
        </Stack>
      </Table.Td>
      <Table.Td width={125} style={{ verticalAlign: 'top' }}>
        <Skeleton visible={!loading}>
          <Stack gap={4}>
            <Group gap='xs'>
              <FolderIcon color='grey' />
              <FormattedNumber value={list?.rowCount || 0} /> taxa
            </Group>
            <Group gap='xs'>
              <FontAwesomeIcon
                fontSize={14}
                icon={list?.isPrivate ? faEyeSlash : faEye}
                color='grey'
              />
              {list?.isPrivate ? 'Private' : 'Public'}
            </Group>
          </Stack>
        </Skeleton>
      </Table.Td>
    </Table.Tr>
  );
}
