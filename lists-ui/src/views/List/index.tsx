/* eslint-disable react-hooks/exhaustive-deps */
import {
  Facet,
  FilteredSpeciesList,
  KV,
  performGQLQuery,
  queries,
  SpeciesList,
  SpeciesListItem,
  SpeciesListSubmit,
} from '#/api';
import {
  ActionIcon,
  Box,
  Center,
  Collapse,
  Container,
  em,
  Flex,
  Grid,
  Group,
  Pagination,
  Paper,
  Select,
  Skeleton,
  Space,
  Stack,
  Table,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import {
  useDebouncedValue,
  useDisclosure,
  useDocumentTitle,
  useMediaQuery,
  useMounted,
} from '@mantine/hooks';
import {
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
  useQueryState,
} from 'nuqs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';
import { Outlet, useLocation, useParams } from 'react-router';

// Icons
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';
import { faAngleRight, faMagnifyingGlass, faXmark } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import tableClasses from './classes/Table.module.css';

// Table components
import { ThCreate } from './components/Table/ThCreate';
import { ThEditable } from './components/Table/ThEditable';
import { TrItem } from './components/Table/TrItem';

// Local component imports
import { ActiveFilters, FiltersSection, ToggleFiltersButton } from '#/components/FiltersSection';
import { IngestProgress } from '#/components/IngestProgress';
import { Message } from '#/components/Message';
import PageLoader from '#/components/PageLoader';
import { getErrorMessage, parseAsFilters } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';
import { getAccessToken } from '#/helpers/utils/getAccessToken';
import { Actions } from './components/Actions';
import { Dates } from './components/Dates';
import { Flags } from './components/Flags';
import { SpeciesItemDrawer } from './components/SpeciesItemDrawer';
import { Summary } from './components/Summary';
import { ThSortable } from './components/Table/ThSortable';

// Styles
import { Breadcrumbs } from '../Dashboard/components/Breadcrumbs';
import classes from './classes/index.module.css';

interface ListLoaderData {
  meta: SpeciesList;
  list: FilteredSpeciesList;
  facets: Facet[];
}

enum SortDirection {
  ASC = 'asc',
  DESC = 'desc',
}

const maxEntries = 10000; // ES maximumDocuments limit (see elastic.maximumDocuments config in lists-service)
const classificationFields = ['family', 'kingdom', 'vernacularName', 'matchType'];

export function List() {
  const { id } = useParams();
  const [_data, setData] = useState<ListLoaderData | null>(null);
  const [loading, setLoading] = useState(true);

  const [list, setList] = useState<FilteredSpeciesList | null>(null);
  const [meta, setMeta] = useState<SpeciesList | null>(null);

  useDocumentTitle(meta?.title || 'Loading...');
  const isMobile = useMediaQuery(`(max-width: ${em(750)})`) || false;

  // Search
  const [search, setSearch] = useQueryState<string>(
    'search',
    parseAsString.withDefault('')
  );
  const [searchDebounced] = useDebouncedValue(search, 300);

  // Search params state
  const [page, setPage] = useQueryState<number>(
    'page',
    parseAsInteger.withDefault(0)
  );
  const [size, setSize] = useQueryState<number>(
    'size',
    parseAsInteger.withDefault(20)
  );
  const [filters, setFilters] = useQueryState<KV[]>('filters', parseAsFilters);
  const [sort, setSort] = useQueryState<string>(
    'sort',
    parseAsString.withDefault('scientificName')
  );
  const [dir, setDir] = useQueryState<SortDirection>(
    'dir',
    parseAsStringEnum<SortDirection>(Object.values(SortDirection)).withDefault(
      SortDirection.ASC
    )
  );

  // Filters display state
  const [hidefilters, setHideFilters] = useQueryState<boolean>('hideFilters', parseAsBoolean.withDefault(false)); 
  const toggleFilters = () => setHideFilters((o) => !o);

  // Internal state (not driven by search params)
  const [facets, setFacets] = useState<Facet[]>([]);
  const [error, setError] = useState<Error | null>(null);
  const [refresh, setRefresh] = useState<boolean>(false);
  const [editing, setEditing] = useState<boolean>(false);
  const [rematching, setRematching] = useState<boolean>(false);
  const [lastProgress, setLastProgress] = useState<boolean>(false);
  const [pageTitle, setPageTitle] = useState<string | null>(null);
  const [paginationLoading, setPaginationLoading] = useState<boolean>(false);

  const location = useLocation();
  const mounted = useMounted();
  const ala = useALA();
  const intl = useIntl();

  // Selection drawer
  const [opened, { open, close }] = useDisclosure();
  const [selected, setSelected] = useState<SpeciesListItem | null>(null);
  // If we're on the reingest page
  const isReingest = location.pathname.endsWith('reingest');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const token = getAccessToken();
        const result = await performGQLQuery(
          queries.QUERY_LISTS_GET,
          {
            speciesListID: id,
            size,
          },
          token
        );

        if (result.meta === null || result.list === null) {
          throw new Error('List not found');
        }

        setData(result);
        setList(result.list);
        setMeta(result.meta);
        setPageTitle(result.meta.title);
        setFacets(result.facets);
      } catch (err) {
        setError(err as Error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id]);

  // Destructure results & calculate the real page offset
  const { totalElements, totalPages } = list || { totalElements: 0, totalPages: 0 };
  let totalEntries = totalElements;

  if (totalElements == maxEntries) {
    totalEntries = meta?.rowCount ?? totalElements;
  }

  const realPage = page + 1;
  const startPage = (realPage - 1) * size + 1;
  const endPage = Math.min(realPage * size, totalEntries || 0);

  // Request abort controller
  const controller = useRef<AbortController | null>(null);

  // Update the search query
  useEffect(() => {
    async function runQuery() {
      try {
        if (controller.current)
          controller.current.abort('New GraphQL request invoked');
        controller.current = new AbortController();

        const {
          meta: updatedMeta,
          list: updatedList,
          facets: updatedFacets,
        } = await performGQLQuery(
          queries.QUERY_LISTS_GET,
          {
            speciesListID: id,
            searchQuery: searchDebounced,
            page,
            size,
            filters, // filters: toKV(filters),
            isPrivate: false,
            sort,
            dir,
          },
          ala.token,
          controller.current.signal
        );

        controller.current = null;

        setError(null);
        setMeta(updatedMeta);
        setList(updatedList);
        setFacets(updatedFacets);
      } catch (error) {
        if (error !== 'New GraphQL request invoked') {
          setError(error as Error);
        }
      } finally {
        setPaginationLoading(false);
      }
    }

    if (mounted) runQuery();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, filters, searchDebounced, refresh, sort, dir, isReingest]);

  // Keep the current page in check
  useEffect(() => {
    if (totalPages && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages]);

  const handleSortClick = useCallback(
    (newSort: string) => {
      if (sort === newSort) {
        setDir(
          dir === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC
        );
      } else {
        setSort(newSort);
        setDir(SortDirection.DESC);
      }
    },
    [sort, dir]
  );

  useEffect(() => {
    // Hide filters for mobile devices
    setHideFilters(isMobile);
  }, [isMobile]);

  const resetFilters = useCallback(
    () => {
      setPage(0); // Reset 'page' when filters are reset
      setFilters([]);
    },[filters]
  );

  const handleSizeChange = (newSize: string | null) => {
    const newSizeInt = parseInt(newSize || '20');
    // Ensure we don't try to query beyond what exists
    if (realPage * newSizeInt > totalElements) {
      setPage(Math.floor(totalElements / newSizeInt));
    }

    setSize(newSizeInt);
  };

  // Retry handler
  const handleRetry = useCallback(() => {
    setPage(0);
    setSize(20);
    setSearch('');
    setRefresh(!refresh);
  }, [refresh]);

  // Item edit handler
  const handleItemEdited = useCallback(
    (item: SpeciesListItem) => {
      setSelected(item);

      // Update the item in the list state
      setList((prevList) => ({
        ...prevList!,
        content: prevList!.content.map((orig) =>
          orig.id === item.id ? item : orig
        ),
      }));
    },
    []
  );

  // Item edit handler
  const handleItemDeleted = useCallback(
    (id: string) => {
      close();
      setList((prevList) => ({
        ...prevList!,
        content: prevList!.content.filter((item) => item.id !== id),
      }));
    },
    []
  );

  // Field deletion handler
  const handleFieldCreated = useCallback(
    (field: string, defaultValue?: string) => {
      setMeta((prevMeta) => ({
        ...prevMeta!,
        fieldList: [...prevMeta!.fieldList, field],
      }));

      // Update list properties to reflect new field
      setList((prevList) => ({
        ...prevList!,
        content: prevList!.content.map((item) => ({
          ...item,
          properties: [
            ...item.properties,
            { key: field, value: defaultValue || '' },
          ],
        })),
      }));
    },
    []
  );

  // Field deletion handler
  const handleFieldDeleted = useCallback(
    (deletedField: string) => {
      setMeta((prevMeta) => ({
        ...prevMeta!,
        fieldList: prevMeta!.fieldList.filter((field) => field !== deletedField),
      }));

      // Update list properties to reflect new item name
      setList((prevList) => ({
        ...prevList!,
        content: prevList!.content.map((item) => ({
          ...item,
          properties: item.properties.filter(({ key }) => key !== deletedField),
        })),
      }));
    },
    []
  );

  // Field deletion handler
  const handleFieldRenamed = useCallback(
    (from: string, to: string) => {
      // Update new list meta to update renamed field
      setMeta((prevMeta) => ({
        ...prevMeta!,
        fieldList: prevMeta!.fieldList.map((field) => (field === from ? to : field)),
      }));

      // Update list properties to reflect new item name
      setList((prevList) => ({
        ...prevList!,
        content: prevList!.content.map((item) => ({
          ...item,
          properties: item.properties.map(({ key, value }) =>
            key === from ? { key: to, value } : { key, value }
          ),
        })),
      }));
    },
    []
  );

  const handleRowClick = useCallback((item: SpeciesListItem) => {
    setSelected(item);
    open();
  }, []);

  const handleAddClick = useCallback(() => {
    setSelected(null);
    open();
  }, []);

  const handleFilterClick = useCallback(
    (filter: KV) => {
      if (
        (filters || []).find(
          ({ key, value }) => filter.key === key && filter.value === value
        )
      ) {
        setFilters(
          (filters || []).filter(
            ({ key, value }) => filter.key !== key || filter.value !== value
          )
        );
      } else {
        setFilters([...(filters || []), filter]);
      }
    },
    [filters]
  );
  
  const handleListMetaUpdated = useCallback(
    (item: SpeciesListSubmit) => {
      setMeta((prevMeta) => ({
        ...prevMeta!,
        ...item,
      }));
    },
    []
  );

  if (loading) {
    return <PageLoader />;
  }

  if (error) {
    return <Message title={intl.formatMessage({ id: 'list.error.title', defaultMessage: 'An error occurred' })}  subtitle={getErrorMessage(error)} />;
  }

  const hasError = Boolean(error);

  return (
    <>
      <SpeciesItemDrawer
        opened={opened}
        item={selected}
        meta={meta!} 
        setRefresh={setRefresh}
        onClose={close}
        onEdited={handleItemEdited}
        onDeleted={handleItemDeleted}
      />
      <Container fluid className={classes.speciesHeader}>
        <Grid>
          <Grid.Col span={12}>
            <Breadcrumbs listTitle={pageTitle ?? undefined} />
          </Grid.Col>
          <Grid.Col span={12}>
            <Title order={4} classNames={{root: classes.title}}>
              <Text classNames={{root: classes.listTitlePrefix}} span inherit>
                <FormattedMessage id='list.title.prefix' defaultMessage='List details' />{' '}
                <FontAwesomeIcon icon={faAngleRight} size="xs" className={classes.listTitleSeparator} />{' '}
              </Text>
              {meta?.title}
            </Title>
          </Grid.Col>
        </Grid>
      </Container>
      <Container fluid className={classes.listDetails}>
        <Grid>
          <Grid.Col span={12} pb={6} mt='lg'>
            <Flex direction='row' justify='space-between' gap={16}>
              <Stack gap='xs' mb={14}>
                {meta?.description && (
                  <Text c='dark-grey-1' size='sm' mt='xs' opacity={0.75}>
                    {meta.description}
                  </Text>
                )}
                <Summary meta={meta!} />
                <Group gap={6} mt={0}>
                  <Flags meta={meta!} />
                  <Dates meta={meta!} />
                </Group>
              </Stack>
              {!isReingest && (
                <Actions
                  meta={meta!}
                  editing={editing}
                  rematching={rematching}
                  onEditingChange={setEditing}
                  onMetaEdited={handleListMetaUpdated}
                  handleAddClick={handleAddClick}
                  onRematch={() => {
                    setRematching(true);
                  }}
                />
              )}
            </Flex>
            
          </Grid.Col>
          { rematching && (
            <Grid.Col span={12}>
              <IngestProgress
                ingesting={rematching}
                id={id || ''}
                disableNavigation={true}
                onProgress={(progress) => {
                  if (
                    progress.elasticTotal === progress.rowCount &&
                    lastProgress
                  ) {
                    setRematching(false);
                    setRefresh(!refresh);
                    setLastProgress(false);
                  } else if (
                    progress.elasticTotal < progress.rowCount &&
                    !lastProgress
                  ) {
                    setLastProgress(true);
                  }
                }}
              />
            </Grid.Col>
          )}
          {isReingest ? (
            <Grid.Col span={12}>
              <Outlet />
            </Grid.Col>
          ) : (
            <>
              <Grid.Col span={12}>
                <Group>
                  { !isMobile && (
                    <ToggleFiltersButton toggleFilters={toggleFilters} hidefilters={hidefilters} />
                  )}
                  <TextInput
                    style={{ flexGrow: 1 }}
                    disabled={hasError}
                    value={search}
                    onChange={(event) => {
                      setSearch(event.currentTarget.value);
                      setPage(0);
                    }}
                    placeholder={intl.formatMessage({ id: 'search.input.placeholder', defaultMessage: 'Search within list' })}
                    aria-label={intl.formatMessage({ id: 'search.input.label', defaultMessage: 'Search within list' })}
                    w={200}
                    leftSection={<FontAwesomeIcon icon={faMagnifyingGlass} fontSize={16} stroke='2' />}
                    rightSection={
                      <ActionIcon
                        radius='sm'
                        variant='transparent'
                        size='xs'
                        title={intl.formatMessage({ id: 'search.clear.label', defaultMessage: 'Clear search' })}
                        aria-label={intl.formatMessage({ id: 'search.clear.label', defaultMessage: 'Clear search' })}
                        disabled={search.length === 0}
                        onClick={() => setSearch('')}
                        style={{ marginLeft: 5, marginRight: 10 }}
                      >
                      <FontAwesomeIcon icon={faXmark} fontSize={20} />
                      </ActionIcon>
                    }
                  />
                  <Select
                    disabled={hasError}
                    w={140}
                    value={size?.toString()}
                    onChange={handleSizeChange}
                    data={['10', '20', '50', '100'].map((value) => ({
                      label: `${value} ${intl.formatMessage({ id: 'size.items', defaultMessage: 'items' })}`,
                      value,
                    }))}
                    aria-label={intl.formatMessage({ id: 'list.page.size.label', defaultMessage: 'Select number of results' })}
                  />
                  { isMobile && (
                    <ToggleFiltersButton toggleFilters={toggleFilters} hidefilters={hidefilters} />
                  )}
                </Group>
              </Grid.Col>
              {/* Filters appear here */}
              {!hidefilters && (
                <Grid.Col span={{ base: 12, sm: 4, md: 3, lg: 2 }} mt={5}>
                  <Collapse in={!hidefilters}>
                      <FiltersSection
                        facets={facets || []}
                        active={filters || []}
                        onSelect={handleFilterClick}
                        onReset={() => {setFilters([]); setPage(0);}}
                      />
                  </Collapse>
                </Grid.Col>
              )}
              <Grid.Col span={{ base: 12, sm: hidefilters ? 12 : 8, md: hidefilters ? 12 : 9, lg: hidefilters ? 12 : 10 }}>
                { (totalElements && totalElements > 0) ? (
                  <>
                    <Text size='sm' mb={6} mt={6} className={classes.resultsSummary} component='span'>
                      <FormattedMessage id='results.showing' defaultMessage='Showing' /> {' '}
                        {startPage}-{endPage} of {' '}
                      <FormattedNumber value={totalEntries || 0} /> {' '}
                      <FormattedMessage id='results.records' defaultMessage='records' /> {' '}
                      { meta?.distinctMatchCount &&
                        <>
                          {'('}
                          {new Intl.NumberFormat().format(meta?.distinctMatchCount ?? 0)}{' '}
                          {intl.formatMessage({
                            id: 'actions.distinct',
                            defaultMessage: 'distinct taxa',
                          })}
                          {')'}
                        </>
                      }
                      { filters && filters.length > 0 && (
                        <><Space w={5} />–<Space w={2} /></>
                      )}
                      { endPage == maxEntries &&
                        <Text style={{ color: 'red', marginLeft: 5 }}>
                          <FormattedMessage id='results.warning.max' defaultMessage='Maximum number of pages reached. Try filtering or sorting table by a different column.' />
                        </Text>
                      }
                    </Text>
                  </>
                ) : (
                  <Text size='sm' mb={6} mt={4} className={classes.resultsSummary} component='span'>
                    <FormattedMessage id='results.noRecords' defaultMessage='No records found '/>
                    { search && search.length > 0 ? (
                      <>{' '} <FormattedMessage id='for' defaultMessage='for'/> "{search}"{' '}</>
                    ) : (
                      <>{' '}</>
                    )}
                    { filters && filters.length > 0 && (
                      <><FormattedMessage id='with' defaultMessage='with'/>{' '}</>
                    )}
                  </Text>
                )}
                { filters && filters.length > 0 && (
                  <Paper
                    ml={4} 
                    className={classes.resultsSummary}
                  >
                    <ActiveFilters
                      active={filters}
                      handleFilterClick={handleFilterClick}
                      resetFilters={resetFilters}
                    />
                  </Paper>
                )}
                <Box style={{ overflowX: 'auto' }}>
                  {error ? (
                  <Message
                    title={intl.formatMessage({ id: 'list.page.error.title', defaultMessage: 'An error occured' })}
                    subtitle={getErrorMessage(error)}
                    icon={<StopIcon size={18} />}
                    action={intl.formatMessage({ id: 'retry', defaultMessage: 'Retry' })}
                    onAction={handleRetry}
                  />
                  ) : totalElements === 0 ? (
                  <Message />
                  ) : paginationLoading ? (
                    <Stack gap="xs" mt={4}>
                      {[...Array(size)].map((_, i) => (
                      <Skeleton key={i} height={i === 0 ? 42 : 30} radius={4} />
                      ))}
                    </Stack>
                  ) : (
                  <Table
                    highlightOnHover
                    classNames={tableClasses}
                    withColumnBorders
                    withRowBorders
                  >
                    <Table.Thead>
                    <Table.Tr>
                      <ThSortable
                      active={sort === 'scientificName'}
                      dir={dir}
                      onSort={() => handleSortClick('scientificName')}
                      >
                      <FormattedMessage id='suppliedName' defaultMessage='Supplied name' />
                      </ThSortable>
                      <ThSortable
                      active={sort === 'classification.scientificName'}
                      dir={dir}
                      onSort={() =>
                        handleSortClick('classification.scientificName')
                      }
                      >
                      <FormattedMessage
                        id='scientificName'
                        defaultMessage='Scientific name'
                      />
                      </ThSortable>
                      {meta!.fieldList.map((field) => (
                      <ThEditable
                        key={field}
                        id={meta!.id}
                        editing={editing}
                        field={field}
                        token={ala.token || ''}
                        onDelete={() => handleFieldDeleted(field)}
                        onRename={(newField) =>
                        handleFieldRenamed(field, newField)
                        }
                      />
                      ))}
                      {editing && (
                      <ThCreate
                        id={meta!.id}
                        token={ala.token || ''}
                        onCreate={handleFieldCreated}
                      />
                      )}
                      {classificationFields.map((field) => (
                      <ThSortable
                        key={field}
                        active={sort === `classification.${field}`}
                        dir={dir}
                        onSort={() =>
                        handleSortClick(`classification.${field}`)
                        }
                      >
                        <FormattedMessage
                        id={`classification.${field ? field : 'none'}`}
                        />
                      </ThSortable>
                      ))}
                    </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                    {list!.content.map((item) => (
                      <TrItem
                      key={item.id}
                      row={item}
                      fields={meta!.fieldList}
                      classification={classificationFields}
                      editing={editing}
                      onClick={() => handleRowClick(item)}
                      />
                    ))}
                    </Table.Tbody>
                  </Table>
                  )}
                  <Center mt='xl'>
                  <Pagination
                    disabled={(totalPages || 0) < 1 || hasError}
                    value={realPage}
                    onChange={(value) => {
                      if (paginationLoading) return; // better than setting paginationLoading in disabled property as UI jitters when that is used
                      setPaginationLoading(true);
                      setPage(value - 1);
                    }}
                    total={totalPages || 9}
                    radius='md'
                    siblings={2}
                    getControlProps={(control) => ({
                      'aria-label': `${control} page`,
                    })}
                    getItemProps={(page) => {
                      // Hide the last page number button (but keep navigation arrows)
                      if (page === totalPages) {
                        return {
                        style: { display: 'none' }
                        };
                      }
                      return {};
                    }}
                  /> 
                  </Center>
                </Box>
              </Grid.Col>
              <Grid.Col span={12} py='xl'>
              </Grid.Col>
            </>
          )}
        </Grid>
      </Container>
    </>
  );
}

export default List;