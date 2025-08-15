/* eslint-disable react-hooks/exhaustive-deps */
import { Facet, KV, queries, SpeciesListPage, useGQLQuery } from '#/api';
import {
  ActionIcon,
  Box,
  Button,
  Center,
  Checkbox,
  Collapse,
  Container,
  em,
  Grid,
  Group,
  Pagination,
  Paper,
  SegmentedControl,
  Select,
  Skeleton,
  Space,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core';
import {
  useDebouncedValue,
  useDocumentTitle,
  useMediaQuery,
} from '@mantine/hooks';
import {
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  useQueryState,
} from 'nuqs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';

// Icons
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';
import {
  faCode,
  faEye,
  faEyeSlash,
  faMagnifyingGlass,
  faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

// Project components
import { Message } from '#/components/Message';
import { ListRow } from './components/ListRow';

// Helpers
import {
  ActiveFilters,
  FiltersSection,
  ToggleFiltersButton,
} from '#/components/FiltersSection';
import { getErrorMessage, parseAsFilters } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

// Styles
import { Link } from 'react-router';
import { Breadcrumbs } from '../Dashboard/components/Breadcrumbs';
import classes from './classes/index.module.css';

interface HomeQuery {
  lists: SpeciesListPage;
  facets: Facet[];
}

const sortField = [
  'relevance_desc',
  'lastUpdated_desc',
  'lastUpdated_asc',
  'title_asc',
  'title_desc',
  'rowCount_desc',
  'rowCount_asc',
];

function Home() {
  useDocumentTitle('ALA Species Lists');
  const isMobile = useMediaQuery(`(max-width: ${em(750)})`) || false;
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
    parseAsString.withDefault('relevance')
  );
  const [dir, setDir] = useQueryState<string>(
    'dir',
    parseAsString.withDefault('desc')
  );
  const [view, setView] = useQueryState<string>(
    'view',
    parseAsString.withDefault('public')
  );
  // Shows the user's lists (my lists) when true
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
  const [hidefilters, setHideFilters] = useQueryState<boolean>(
    'hideFilters',
    parseAsBoolean.withDefault(false)
  );
  const toggleFilters = () => setHideFilters((o) => !o);

  const ala = useALA();
  const { data, error, loading, update } = useGQLQuery<HomeQuery>(
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

  useEffect(() => {
    // Hide filters for mobile devices
    setHideFilters(isMobile);
  }, [isMobile]);

  // Retry handler
  const handleRetry = useCallback(() => {
    setPage(0);
    setSize(10);
    setSort('relevance_desc');
    setDir('desc');
    setSearch('');
    setView('public');
    setIsUser(false);
    setRefresh(!refresh);
  }, [refresh]);

  const sortOptions = useMemo(
    () =>
      sortField.map((key) => ({
        label: intl.formatMessage({
          id: key,
          defaultMessage: key.replace(/_/g, ' '),
        }),
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

  const resetFilters = useCallback(() => {
    setPage(0); // Reset 'page' when filters are reset
    setFilters([]);
  }, [filters]);

  const labels = useMemo(
    () => [
      {
        value: 'public',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faEye} fontSize={14} />
            <span>
              <FormattedMessage id='public.label' defaultMessage='Public' />
            </span>
          </Center>
        ),
      },
      {
        value: 'private',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faEyeSlash} fontSize={14} />
            <span>
              <FormattedMessage id='private.label' defaultMessage='Private' />
            </span>
          </Center>
        ),
      },
    ],
    [ala.isAdmin]
  );

  const hasError = Boolean(error);

  return (
    <>
      <Container fluid className={classes.speciesHeader}>
        <Grid>
          <Grid.Col span={12}>
            <Breadcrumbs listTitle={undefined} />
          </Grid.Col>
          <Grid.Col span={12}>
            <Title order={3} classNames={{ root: classes.title }}>
              <FormattedMessage
                id='lists.title'
                defaultMessage='Species Lists'
              />
            </Title>
          </Grid.Col>
          <Grid.Col span={9}>
            <Title order={5} classNames={{ root: classes.subtitle }}>
              <FormattedMessage
                id='lists.subtitle'
                defaultMessage='A tool for finding species checklists'
              />
            </Title>
          </Grid.Col>
          <Grid.Col
            span={3}
            style={{ display: 'flex', justifyContent: 'flex-end' }}
          >
            <Tooltip
              label={intl.formatMessage({ id: 'openapi.button.title' })}
              position='left'
              withArrow
              multiline
              w={200}
              opacity={0.8}
            >
              <Button
                variant='default'
                radius='xl'
                component={Link}
                to={import.meta.env.VITE_API_BASEURL}
                target='openapi'
                rel='noopener noreferrer'
                title={intl.formatMessage({ id: 'openapi.button.title' })}
                aria-label={intl.formatMessage({ id: 'openapi.button.title' })}
                leftSection={<FontAwesomeIcon icon={faCode} />}
              >
                <FormattedMessage
                  id='openapi.button.label'
                  defaultMessage='OpenAPI'
                />
              </Button>
            </Tooltip>
          </Grid.Col>
        </Grid>
      </Container>
      <Container fluid mt='lg'>
        <Grid>
          <Grid.Col span={12}>
            <Group>
              {!isMobile && (
                <ToggleFiltersButton
                  toggleFilters={toggleFilters}
                  hidefilters={hidefilters}
                />
              )}
              <TextInput
                style={{ flexGrow: 1 }}
                disabled={!data || hasError}
                value={search}
                onChange={(event) => {
                  setPage(0); // Reset 'page' when search is changed
                  setSearch(event.currentTarget.value);
                }}
                placeholder={intl.formatMessage({
                  id: 'search.input.placeholder',
                  defaultMessage: 'Search lists by name or taxa',
                })}
                w={200}
                leftSection={
                  <FontAwesomeIcon
                    icon={faMagnifyingGlass}
                    fontSize={16}
                    stroke='2'
                  />
                }
                rightSection={
                  <ActionIcon
                    radius='sm'
                    variant='transparent'
                    size='xs'
                    title={intl.formatMessage({
                      id: 'search.clear.label',
                      defaultMessage: 'Clear search',
                    })}
                    aria-label={intl.formatMessage({
                      id: 'search.clear.label',
                      defaultMessage: 'Clear search',
                    })}
                    disabled={search.length === 0}
                    onClick={() => setSearch('')}
                    style={{ marginLeft: 5, marginRight: 10 }}
                  >
                    <FontAwesomeIcon icon={faXmark} fontSize={20} />
                  </ActionIcon>
                }
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
                    label={
                      <FormattedMessage
                        id='myLists.label'
                        defaultMessage='My Lists'
                      />
                    }
                    checked={isUser}
                    size='sm'
                    onChange={(e) => setIsUser(e.currentTarget.checked)}
                    classNames={{ label: classes.myListsLabel }}
                  />
                </>
              )}
              <Select
                w={235}
                value={`${sort}_${dir}`}
                label={
                  <FormattedMessage id='sort.label' defaultMessage='Sort by' />
                }
                withCheckIcon={true}
                data={sortOptions}
                classNames={{
                  root: classes.sortSelectRoot,
                  label: classes.sortSelectLabel,
                }}
                onChange={(value: string | null) => {
                  const [sort, dir] = (value || 'relevance_desc').split('_');
                  setSort(sort);
                  setDir(dir);
                  setPage(0);
                }}
                disabled={!data || hasError}
                aria-label={intl.formatMessage({
                  id: 'sort.field.ariaLabel',
                  defaultMessage: 'Select field to sort results',
                })}
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
                aria-label={intl.formatMessage({
                  id: 'page.size.ariaLabel',
                  defaultMessage: 'Select number of results',
                })}
              />
              {isMobile && (
                <ToggleFiltersButton
                  toggleFilters={toggleFilters}
                  hidefilters={hidefilters}
                />
              )}
            </Group>
          </Grid.Col>
          {!hidefilters && (
            <Grid.Col span={{ base: 12, sm: 4, md: 3, lg: 2 }} mt={16}>
              <Collapse in={!hidefilters}>
                {/* Filters appear here */}
                <FiltersSection
                  facets={data?.facets || []}
                  active={filters || []}
                  onSelect={handleFilterClick}
                  onReset={() => {
                    setFilters([]);
                    setPage(0);
                  }}
                />
              </Collapse>
            </Grid.Col>
          )}
          <Grid.Col
            span={{
              base: 12,
              sm: hidefilters ? 12 : 8,
              md: hidefilters ? 12 : 9,
              lg: hidefilters ? 12 : 10,
            }}
          >
            <Grid.Col span={12}>
              {error && (
                <Message
                  title={intl.formatMessage({
                    id: 'error.error.title',
                    defaultMessage: 'An error occurred',
                  })}
                  subtitle={getErrorMessage(error)}
                  icon={<StopIcon size={18} />}
                  action={intl.formatMessage({
                    id: 'error.action.label',
                    defaultMessage: 'Retry',
                  })}
                  onAction={handleRetry}
                />
              )}
              {!error && (
                <>
                  <Box ml={10} mb={0} mt={5}>
                    {totalElements && totalElements > 0 ? (
                      <>
                        <Text
                          size='sm'
                          mb={6}
                          mt={4}
                          className={classes.resultsSummary}
                          component='span'
                        >
                          <FormattedMessage
                            id='results.showing'
                            defaultMessage='Showing'
                          />{' '}
                          {(realPage - 1) * size + 1}-
                          {Math.min(
                            (realPage - 1) * size + size,
                            totalElements || 0
                          )}{' '}
                          of <FormattedNumber value={totalElements || 0} />{' '}
                          <FormattedMessage
                            id='results.records'
                            defaultMessage='records'
                          />
                          {filters && filters.length > 0 && (
                            <>
                              <Space w={5} />–<Space w={2} />
                            </>
                          )}
                        </Text>
                      </>
                    ) : (
                      <Skeleton
                        mb={6}
                        mt={4}
                        visible={!totalElements && loading}
                      >
                        <Text
                          size='sm'
                          className={classes.resultsSummary}
                          component='span'
                        >
                          <FormattedMessage
                            id='results.noRecords'
                            defaultMessage='No records found'
                          />
                          {search && search.length > 0 ? (
                            <>
                              {' '}
                              <FormattedMessage
                                id='results.for'
                                defaultMessage='for'
                              />{' '}
                              "{search}"{' '}
                            </>
                          ) : (
                            <> </>
                          )}
                          {filters && filters.length > 0 && <>with </>}
                        </Text>
                      </Skeleton>
                    )}
                    {filters && filters.length > 0 && (
                      <Paper ml={4} className={classes.resultsSummary}>
                        <ActiveFilters
                          active={filters}
                          handleFilterClick={handleFilterClick}
                          resetFilters={resetFilters}
                        />
                      </Paper>
                    )}
                  </Box>
                  <Table
                    striped={false}
                    withRowBorders
                    className={classes.resultsTable}
                  >
                    <Table.Tbody>
                      {content
                        ? content.map((list) => (
                            <ListRow key={list.id} list={list} />
                          ))
                        : Array.from(Array(size).keys()).map((key) => (
                            <ListRow key={key} />
                          ))}
                    </Table.Tbody>
                  </Table>
                </>
              )}
              {totalElements === 0 && (
                <Message
                  title={intl.formatMessage({
                    id: 'error.noListsFound.title',
                    defaultMessage: 'No lists found',
                  })}
                  subtitle={intl.formatMessage({
                    id: 'error.noListsFound.subTitle',
                    defaultMessage:
                      'Try removing a filter or searching with a different query',
                  })}
                />
              )}
              <Stack align='center' justify='center' gap='xs' w='100%' py='xl'>
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
    </>
  );
}

export default Home;
