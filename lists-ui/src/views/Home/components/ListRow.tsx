import { Anchor, Badge, Group, Skeleton, Stack, Table, Text } from '@mantine/core';
import { FormattedMessage, FormattedNumber } from 'react-intl';
import { Link } from 'react-router';
import { formatISO } from 'date-fns';
import { parseDate } from '#/helpers/utils/parseListDate';
import { SpeciesList } from '#/api';
import { FolderIcon } from '@atlasoflivingaustralia/ala-mantine';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEye, faEyeSlash } from '@fortawesome/free-regular-svg-icons';

interface ListRowProps {
  list?: SpeciesList;
}

export function ListRow({ list }: ListRowProps) {
  const loading = Boolean(list);
  const lastUpdatedObj: Date | undefined = parseDate(list?.lastUpdated || '');
  const lastUpdated: string = lastUpdatedObj instanceof Date && !isNaN(lastUpdatedObj.getTime()) 
      ? formatISO(lastUpdatedObj, { representation: 'date' }) 
      : 'Unknown';

  return (
    <Table.Tr>
      <Table.Td>
        <Stack gap={4} pb='md'>
          <Skeleton visible={!loading}>
            <Anchor size='md' fw={600} component={Link} to={`list/${list?.id}`}>
              {list?.title || 'List title'}
            </Anchor>
          </Skeleton>
          <Skeleton w='50%' visible={!loading}>
            <Group gap='xs'>
              <Badge 
                  variant="outline" 
                  color="dark.3" 
                  size="md"
                  radius="md"
                  styles={{
                    label: {
                      textTransform: 'none',
                      fontSize: 13,
                    },
                  }}
              >
                <FormattedMessage id={list?.listType || 'OTHER'} />
                </Badge>
              <Text size='sm' c='dark.5'><FormattedMessage id="date.lastUpdated" />: {lastUpdated}</Text>
            </Group>
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
