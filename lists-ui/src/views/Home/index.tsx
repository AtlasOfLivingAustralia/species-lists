/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Center,
  Checkbox,
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
import { useDebouncedValue, useDocumentTitle } from '@mantine/hooks';
import { FormattedNumber } from 'react-intl';
import {
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  useQueryState,
} from 'nuqs';

// Icons
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';

// Project components
import { Message } from '#/components/Message';
import { FiltersDrawer } from '#/components/FiltersDrawer';
import { ListRow } from './components/ListRow';

// Helpers
import { getErrorMessage, parseAsFilters } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

interface HomeQuery {
  lists: SpeciesListPage;
  facets: Facet[];
}

function Home() {
  useDocumentTitle('ALA Lists');

  // Search
  const [search, setSearch] = useQueryState<string>(
    'search',
    parseAsString.withDefault('')
  );
  const [searchDebounced] = useDebouncedValue(search, 300);

  // Search query state
  const [page, setPage] = useQueryState<number>(
    'page',
    parseAsInteger.withDefault(0)
  );
  const [size, setSize] = useQueryState<number>(
    'size',
    parseAsInteger.withDefault(10)
  );
  const [view, setView] = useQueryState<string>(
    'view',
    parseAsString.withDefault('public')
  );
  const [isUser, setIsUser] = useQueryState<boolean>(
    'isUser',
    parseAsBoolean.withDefault(false)
  );
  const [filters, setFilters] = useQueryState<KV[]>(
    'filters', 
    parseAsFilters // Note: adding `.withDefault([])` causes infinite loop (bug in nuqs v2.4.1 ??)
  );

  // Internal state (not driven by search params)
  const [refresh, setRefresh] = useState<boolean>(false);

  const ala = useALA();
  const { data, error, update } = useGQLQuery<HomeQuery>(
    queries.QUERY_LISTS_SEARCH,
    {
      searchQuery: search,
      page,
      size: size,
      filters,
      isPrivate: view === 'private',
      ...(isUser ? { userId: ala.userid } : {}),
    },
    { clearDataOnUpdate: false, token: ala.token }
  );

  // Destructure results & calculate the real page offset
  const { totalElements, totalPages, content } = data?.lists || {};
  const realPage = page + 1;

  // Update the search query
  useEffect(() => {
    update({
      searchQuery: searchDebounced,
      page,
      size,
      filters,
      isPrivate: view === 'private',
      ...(isUser ? { userId: ala.userid } : {}),
    });
  }, [page, size, searchDebounced, filters, refresh, view, isUser]);

  // Keep the current page in check
  useEffect(() => {
    if (totalPages && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages]);

  // Retry handler
  const handleRetry = useCallback(() => {
    setPage(0);
    setSize(10);
    setSearch('');
    setView('public');
    setIsUser(false);
    setRefresh(!refresh);
  }, [refresh]);

  const handleFilterClick = useCallback(
    (filter: KV) => {
      if (
        (filters || []).find(
          ({ key, value }) => filter.key === key && filter.value === value
        )
      ) {
        setPage(0); // Reset 'page' when a filter is removed
        setFilters(
          (filters || []).filter(
            ({ key, value }) => filter.key !== key || filter.value !== value
          )
        );
      } else {
        setPage(0); // Reset 'page' when a filter is added
        setFilters([...(filters || []), filter]);
      }
    },
    [filters]
  );

  const labels = useMemo(
    () => [
      {
        value: 'public',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faEye} fontSize={14} />
            <span>Public</span>
          </Center>
        ),
      },
      {
        value: 'private',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faEyeSlash} fontSize={14} />
            <span>Private</span>
          </Center>
        ),
      },
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
              data={['10', '20', '50', '100'].map((value) => ({
                label: `${value} items`,
                value,
              }))}
              disabled={!data || hasError}
              aria-label='Select number of results'
            />
            <TextInput
              style={{ flexGrow: 1 }}
              disabled={!data || hasError}
              value={search}
              onChange={(event) => {
                setPage(0); // Reset 'page' when search is changed
                setSearch(event.currentTarget.value);
              }}
              placeholder='Search list by name or taxa'
              w={200}
            />
            {ala.isAuthenticated && (
              <>
                <SegmentedControl
                  disabled={!data || hasError}
                  value={view}
                  onChange={setView}
                  radius='md'
                  data={labels}
                />
                <Checkbox
                  label='My Lists'
                  checked={isUser}
                  onChange={(e) => setIsUser(e.currentTarget.checked)}
                />
              </>
            )}
            <FiltersDrawer
              facets={data?.facets || []}
              active={filters || []}
              onSelect={handleFilterClick}
              onReset={() => {setFilters([]); setPage(0);}}
            />
            <Text opacity={0.75} size='sm'>
              {(realPage - 1) * size + 1}-
              {Math.min((realPage - 1) * size + size, totalElements || 0)} of{' '}
              <FormattedNumber value={totalElements || 0} /> records
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
