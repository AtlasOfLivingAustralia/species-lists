import { Anchor, Badge, Group, Skeleton, Space, Stack, Table, Text } from '@mantine/core';
import { FormattedDate, FormattedMessage, FormattedNumber, IntlProvider } from 'react-intl';
import { Link } from 'react-router';
import { parseDate } from '#/helpers/utils/parseListDate';
import { SpeciesList } from '#/api';
import { MapLayersIcon } from '@atlasoflivingaustralia/ala-mantine';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCalendar, faEye, faEyeSlash } from '@fortawesome/free-regular-svg-icons';
import { useALA } from '#/helpers/context/useALA';
import { faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { faStar } from '@fortawesome/free-regular-svg-icons';
import AlaIcon from '#/static/ala-logo-grey.svg?react';
import { ListTypeBadge } from '#/components/ListTypeBadge';

interface ListRowProps {
  list?: SpeciesList;
  isUser?: boolean;
}

export function ListRow({ list, isUser }: ListRowProps) {
  const loading = Boolean(list);
  const ala = useALA();
  const locale = import.meta.env.VITE_LOCALE || 'en-AU';
  
  const lastUpdatedObj: Date | undefined = parseDate(list?.lastUpdated || '');
  const lastUpdated = lastUpdatedObj instanceof Date && !isNaN(lastUpdatedObj.getTime()) ? (
    <IntlProvider locale={locale}><FormattedDate value={lastUpdatedObj} /></IntlProvider>
  ) : 'Unknown';

  return (
    <Table.Tr>
      <Table.Td>
        <Stack gap={4} pb={6} pt={6}>
          <Skeleton visible={!loading}>
            <Anchor size='md' fw={600} component={Link} to={`list/${list?.id}`}>
              {list?.title || 'List title'}
            </Anchor>
          </Skeleton>
          <Skeleton w='70%' visible={!loading}>
            <Group gap='5'>
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
              {ala.isAuthenticated && (isUser || ala.isAdmin) && (
                <>
                  <Space w={3} />
                  <FontAwesomeIcon fontSize={15} color='grey' icon={list?.isPrivate ? faEyeSlash : faEye} />
                  <Text size='sm' fw='400'>
                  {list?.isPrivate ? (
                    <FormattedMessage id="access.private" defaultMessage="Private" />
                  ) : (
                    <FormattedMessage id="access.public" defaultMessage="Public" />
                  )}
                  </Text>
                </>
              )}
              { list?.isAuthoritative && (
                <><Space w={3} /><ListTypeBadge listTypeValue='isAuthoritative'/></>
              )}
              { list?.isSDS && (
                <><Space w={3} /><ListTypeBadge listTypeValue='isSDS'/></>
              )}
              { list?.isBIE && (
                <><Space w={3} /><ListTypeBadge listTypeValue='isBIE'/></>
              )}
              { list?.region && list.region.trim() !== '' && (
                // Not currently used as GraphQL is not reuturning this field
                <><Space w={3} /><ListTypeBadge listTypeValue='hasRegion'/></>
              )}
            </Group>
          </Skeleton>
        </Stack>
      </Table.Td>
      <Table.Td width={150} style={{ verticalAlign: 'top' }}>
        <Skeleton visible={!loading}>
          <Stack gap={4} pb={6} pt={6}>
            <Group gap='4'>
              <MapLayersIcon size={15} color='grey'/>
              <Text size='sm' fw='400' ml={3}><FormattedNumber value={list?.rowCount || 0} />{' '}</Text>
              <Text size='sm' fw='400'>{ list?.rowCount === 1 ? <FormattedMessage id="taxon" /> : <FormattedMessage id="taxa" /> }</Text>
            </Group>
            <Group gap='4'>
              <FontAwesomeIcon icon={faCalendar} fontSize={15} color='grey'/>
              <Text size='sm' fw='400' ml={5}>{lastUpdated}</Text>
            </Group>
          </Stack>
        </Skeleton>
      </Table.Td>
    </Table.Tr>
  );
}
