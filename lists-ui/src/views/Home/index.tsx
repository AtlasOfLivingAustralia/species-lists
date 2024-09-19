/* eslint-disable react-hooks/exhaustive-deps */
import {
  Center,
  Container,
  Grid,
  Group,
  Pagination,
  SegmentedControl,
  Select,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useGQLQuery, queries, SpeciesListPage, KV, Facet } from '#/api';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ListRow } from './components/ListRow';
import { useDebouncedState, useDocumentTitle } from '@mantine/hooks';
import { FormattedNumber } from 'react-intl';
import { Message } from '#/components/Message';
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';
import { getErrorMessage } from '#/helpers';
import { FiltersDrawer } from '#/components/FiltersDrawer';
import { useALA } from '#/helpers/context/useALA';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEyeSlash, faList, faUser } from '@fortawesome/free-solid-svg-icons';

interface HomeQuery {
  lists: SpeciesListPage;
  facets: Facet[];
}

function Home() {
  useDocumentTitle('ALA Lists');

  const [page, setPage] = useState<number>(0);
  const [size, setSize] = useState<number>(10);
  const [searchQuery, setSearch] = useDebouncedState('', 300);
  const [filters, setFilters] = useState<KV[]>([]);
  const [refresh, setRefresh] = useState<boolean>(false);
  const [view, setView] = useState<string>('all');

  const ala = useALA();
  const { data, error, update } = useGQLQuery<HomeQuery>(
    queries.QUERY_LISTS_SEARCH,
    {
      searchQuery: '',
      page,
      size: size,
      filters: [],
      isPrivate: view === 'private',
      ...(ala.isAuthenticated ? { userid: ala.userid } : {}),
    },
    { clearDataOnUpdate: false, token: ala.token }
  );

  // Destructure results & calculate the real page offset
  const { totalElements, totalPages, content } = data?.lists || {};
  const realPage = page + 1;

  // Update the search query
  useEffect(() => {
    update({
      searchQuery,
      page,
      size,
      filters,
      isPrivate: view === 'private',
      ...(ala.isAuthenticated ? { userid: ala.userid } : {}),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, searchQuery, filters, refresh, view]);

  // Keep the current page in check
  useEffect(() => {
    if (totalPages && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages]);

  // Retry handler
  const handleRetry = useCallback(() => {
    setPage(0);
    setSize(10);
    setSearch('');
    setRefresh(!refresh);
  }, [refresh]);

  const handleFilterClick = useCallback(
    (filter: KV) => {
      if (
        filters.find(
          ({ key, value }) => filter.key === key && filter.value === value
        )
      ) {
        setFilters(
          filters.filter(
            ({ key, value }) => filter.key !== key || filter.value !== value
          )
        );
      } else {
        setFilters([...filters, filter]);
      }
    },
    [filters]
  );

  const labels = useMemo(
    () => [
      {
        value: 'all',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faList} fontSize={14} />
            <span>All</span>
          </Center>
        ),
      },
      {
        value: 'my',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faUser} fontSize={14} />
            <span>My</span>
          </Center>
        ),
      },
      ...(ala.isAdmin
        ? [
            {
              value: 'private',
              label: (
                <Center style={{ gap: 10 }}>
                  <FontAwesomeIcon icon={faEyeSlash} fontSize={14} />
                  <span>Private</span>
                </Center>
              ),
            },
          ]
        : []),
    ],
    [ala.isAdmin]
  );

  const hasError = Boolean(error);

  return (
    <Container fluid>
      <Grid>
        <Grid.Col span={12}>
          <Group>
            <Select
              w={110}
              value={size?.toString()}
              onChange={(newSize) => setSize(parseInt(newSize || '10', 10))}
              data={['10', '20', '40'].map((value) => ({
                label: `${value} items`,
                value,
              }))}
              disabled={!data || hasError}
              aria-label='Select number of results'
            />
            <TextInput
              style={{ flexGrow: 1 }}
              disabled={!data || hasError}
              defaultValue={searchQuery}
              onChange={(event) => {
                setSearch(event.currentTarget.value);
              }}
              placeholder='Search list by name or taxa'
              w={200}
            />
            {ala.isAuthenticated && (
              <SegmentedControl
                disabled={!data || hasError}
                value={view}
                onChange={setView}
                radius='md'
                data={labels}
              />
            )}
            <FiltersDrawer
              facets={data?.facets || []}
              active={filters}
              onSelect={handleFilterClick}
              onReset={() => setFilters([])}
            />
            <Text opacity={0.75} size='sm'>
              {(realPage - 1) * size + 1}-
              {Math.min((realPage - 1) * size + size, totalElements || 0)} of{' '}
              <FormattedNumber value={totalElements || 0} /> total records
            </Text>
          </Group>
        </Grid.Col>
        <Grid.Col span={12}>
          {totalElements === 0 && <Message title='No lists found' />}
          {error && (
            <Message
              title='An error occured'
              subtitle={getErrorMessage(error)}
              icon={<StopIcon size={18} />}
              action='Retry'
              onAction={handleRetry}
            />
          )}
          {!error && (
            <Table striped={false} withRowBorders>
              <Table.Tbody>
                {content
                  ? content.map((list) => <ListRow key={list.id} list={list} />)
                  : Array.from(Array(size).keys()).map((key) => (
                      <ListRow key={key} />
                    ))}
              </Table.Tbody>
            </Table>
          )}
        </Grid.Col>
        <Grid.Col span={12} py='xl'>
          <Center>
            <Pagination
              disabled={(totalPages || 0) < 1 || hasError}
              value={realPage}
              onChange={(value) => setPage(value - 1)}
              total={totalPages || 9}
              radius='md'
              getControlProps={(control) => ({
                'aria-label': `${control} page`,
              })}
            />
          </Center>
        </Grid.Col>
      </Grid>
    </Container>
  );
}

export default Home;
