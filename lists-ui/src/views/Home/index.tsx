/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActionIcon,
  Box,
  Button,
  Center,
  Checkbox,
  Collapse,
  Container,
  Grid,
  Group,
  Pagination,
  Paper,
  SegmentedControl,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useGQLQuery, queries, SpeciesListPage, KV, Facet } from '#/api';
import { useDebouncedValue, useDocumentTitle } from '@mantine/hooks';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';
import {
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  useQueryState,
} from 'nuqs';

// Icons
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { IconAdjustmentsHorizontal } from '@tabler/icons-react';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';

// Project components
import { Message } from '#/components/Message';
import { ListRow } from './components/ListRow';

// Helpers
import { getErrorMessage, parseAsFilters } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';
import { ActiveFilters, FiltersSection } from '#/components/FiltersSection';

// Styles
import classes from './classes/index.module.css';

interface HomeQuery {
  lists: SpeciesListPage;
  facets: Facet[];
}

const sortField = [
  'lastUpdated_desc',
  'lastUpdated_asc',
  'title_asc',
  'title_desc',
  'rowCount_desc',
  'rowCount_asc',
];

function Home() {
  useDocumentTitle('ALA Species Lists');
  const intl = useIntl();

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
  const [sort, setSort] = useQueryState<string>(
    'sort',
    parseAsString.withDefault('lastUpdated')
  );
  const [dir, setDir] = useQueryState<string>(
    'dir',
    parseAsString.withDefault('desc')
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

  // Filters display state
  const [hidefilters, setHideFilters] = useQueryState<boolean>('hideFilters', parseAsBoolean.withDefault(false)); 
  const toggleFilters = () => setHideFilters((o) => !o);

  const ala = useALA();
  const { data, error, update } = useGQLQuery<HomeQuery>(
    queries.QUERY_LISTS_SEARCH,
    {
      searchQuery: search,
      page,
      sort,
      dir,
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
      sort,
      dir,
      size,
      filters,
      isPrivate: view === 'private',
      ...(isUser ? { userId: ala.userid } : {}),
    });
  }, [page, size, searchDebounced, sort, dir, filters, refresh, view, isUser]);

  // Keep the current page in check
  useEffect(() => {
    if (totalPages && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages]);

  // Retry handler
  const handleRetry = useCallback(() => {
    setPage(0);
    setSize(10);
    setSort('title');
    setDir('desc');
    setSearch('lastUpdated');
    setView('public');
    setIsUser(false);
    setRefresh(!refresh);
  }, [refresh]);

  const sortOptions = useMemo(() => 
    sortField.map((key) => ({
      label: intl.formatMessage({ id: key, defaultMessage: key.replace(/_/g, ' ') }),
      value: key,
    })), 
    [intl]
  );

  const handleFilterClick = useCallback(
    (filter: KV) => {
      // console.log('Filter clicked:', filter);
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

  const resetFilters = useCallback(
    () => {
      setPage(0); // Reset 'page' when filters are reset
      setFilters([]);
    },[filters]
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
            <Button 
              size= 'sm' 
              leftSection={<IconAdjustmentsHorizontal size={14} />}
              variant='default'
              radius="md"
              fw="normal"
              onClick={toggleFilters}
            >
              { hidefilters 
              ? <FormattedMessage id='filters.hide' defaultMessage='Show Filters' /> 
              : <FormattedMessage id='filters.show' defaultMessage='Hide Filters' /> 
            }
            </Button>
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
            {ala.isAuthenticated && false && (
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
            <Select
              w={235}
              value={`${sort}_${dir}`}
              label={<FormattedMessage id='sort.label' defaultMessage='Sort by' />}
              withCheckIcon={true}
              data={sortOptions}
              styles={{
                root: {
                  display: 'flex',
                  alignItems: 'center',
                },
                label: {
                  marginRight: 10,
                },
              }}
              onChange={(value: string | null) => {
                const [sort, dir] = (value || 'title_asc').split('_');
                setSort(sort);
                setDir(dir);
                setPage(0);
              }}
              disabled={!data || hasError}
              aria-label={intl.formatMessage({ id: 'sort.field.ariaLabel', defaultMessage: 'Select field to sort results' })} 
            />
            <Select
              w={110}
              value={size?.toString()}
              onChange={(newSize) => setSize(parseInt(newSize || '10', 10))}
              data={['10', '20', '50', '100'].map((value) => ({
                label: `${value} items`,
                value,
              }))}
              disabled={!data || hasError}
              aria-label={intl.formatMessage({ id: 'page.size.ariaLabel', defaultMessage: 'Select number of results' })} 
            />
          </Group>
        </Grid.Col>
        {!hidefilters && (
          <Grid.Col span={2} mt={15}>
            <Collapse in={!hidefilters}>
                {/* Filters appear here */}
                <FiltersSection
                  facets={data?.facets || []}
                  active={filters || []}
                  onSelect={handleFilterClick}
                  onReset={() => {setFilters([]); setPage(0);}}
                />
            </Collapse>
          </Grid.Col>
        )}
        <Grid.Col span={hidefilters ? 12 : 10}>
          <Grid.Col span={12}>
            {totalElements === 0 && 
              <Message 
                title={intl.formatMessage({ id: 'error.noListsFound.title', defaultMessage: 'No lists found' })} 
                subtitle={intl.formatMessage({ id: 'error.noListsFound.subTitle', defaultMessage: 'Try removing a filter or searching with a different query' })} 
              />
            }
            {error && (
              <Message
                title={intl.formatMessage({ id: 'error.error.title', defaultMessage: 'An error occurred' })} 
                subtitle={getErrorMessage(error)}
                icon={<StopIcon size={18} />}
                action={intl.formatMessage({ id: 'error.action.label', defaultMessage: 'Retry' })} 
                onAction={handleRetry}
              />
            )}
            {!error && (
              <>
                <Box ml={10} mb={0} mt={5}>
                  { totalElements && totalElements > 0 && (
                    <Text size='sm' mb={6} mt={4} className={classes.resultsSummary} component='span'>
                      <FormattedMessage id='results.showing' defaultMessage='Showing' /> {' '}
                      {(realPage - 1) * size + 1}-
                      {Math.min((realPage - 1) * size + size, totalElements || 0)} of {' '}
                      <FormattedNumber value={totalElements || 0} /> {' '}
                      <FormattedMessage id='results.records' defaultMessage='records' />
                    </Text>
                  )}
                  { filters && filters.length > 0 && (
                    <Paper 
                      ml={4} 
                      // mb={5} 
                      style={{ display: 'inline-flex', alignItems: 'center', fontSize: 'var(--mantine-font-size-sm)' }}
                      className={classes.resultsSummary}
                    >
                      –{' '}
                      <ActiveFilters
                        active={filters}
                        handleFilterClick={handleFilterClick}
                        resetFilters={resetFilters}
                      />
                    </Paper>
                  )}
                </Box>
                <Table striped={false} withRowBorders style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
                  <Table.Tbody>
                    {content
                      ? content.map((list) => <ListRow key={list.id} list={list} />)
                      : Array.from(Array(size).keys()).map((key) => (
                          <ListRow key={key} />
                        ))}
                  </Table.Tbody>
                </Table>
              </>
            )}
            <Stack align="center" justify="center" gap="xs" w="100%" py="xl">
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
            </Stack>
          </Grid.Col>
        </Grid.Col>
      </Grid>
    </Container>
  );
}

export default Home;
