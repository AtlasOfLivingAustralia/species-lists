import { Anchor, Badge, Group, Skeleton, Stack, Table, Text } from '@mantine/core';
import { FormattedMessage, FormattedNumber } from 'react-intl';
import { Link } from 'react-router';
import { formatISO } from 'date-fns';
import { parseDate } from '#/helpers/utils/parseListDate';
import { SpeciesList } from '#/api';
import { FolderIcon } from '@atlasoflivingaustralia/ala-mantine';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEye, faEyeSlash } from '@fortawesome/free-regular-svg-icons';
import { useALA } from '#/helpers/context/useALA';

interface ListRowProps {
  list?: SpeciesList;
  isUser?: boolean;
}

export function ListRow({ list, isUser }: ListRowProps) {
  const loading = Boolean(list);
  const ala = useALA();
  const lastUpdatedObj: Date | undefined = parseDate(list?.lastUpdated || '');
  const lastUpdated: string = lastUpdatedObj instanceof Date && !isNaN(lastUpdatedObj.getTime()) 
      ? formatISO(lastUpdatedObj, { representation: 'date' }) 
      : 'Unknown';

  return (
    <Table.Tr>
      <Table.Td>
        <Stack gap={4} pb={4}>
          <Skeleton visible={!loading}>
            <Anchor size='md' fw={600} component={Link} to={`list/${list?.id}`}>
              {list?.title || 'List title'}
            </Anchor>
          </Skeleton>
          <Skeleton w='50%' visible={!loading}>
            <Group gap='5'>
              <Text size='sm'><FormattedMessage id="date.lastUpdated" defaultMessage="Updated"/>:</Text>
              <Text size='sm' fw='500'>{lastUpdated}</Text>
            </Group>
          </Skeleton>
        </Stack>
      </Table.Td>
      <Table.Td width={150} style={{ verticalAlign: 'top' }}>
        <Skeleton visible={!loading}>
          <Stack gap={4}>
            <Group gap='4'>
              <FolderIcon color='grey'/>
              <Text size='sm' ><FormattedNumber value={list?.rowCount || 0} />{' '}</Text>
              <Text size='sm' >{ list?.rowCount === 1 ? <FormattedMessage id="taxon" /> : <FormattedMessage id="taxa" /> }</Text>
            </Group>
            <Group gap='4'>
              <Badge 
                  variant="outline" 
                  color="gray" 
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
            </Group>
            {ala.isAuthenticated && (isUser || ala.isAdmin) && (
              <Group gap='xs'>
                <FontAwesomeIcon
                  fontSize={14}
                  icon={list?.isPrivate ? faEyeSlash : faEye}
                />
                {list?.isPrivate ? 'Private' : 'Public'}
              </Group>
            )}
          </Stack>
        </Skeleton>
      </Table.Td>
    </Table.Tr>
  );
}
