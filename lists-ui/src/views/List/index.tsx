/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Center,
  Collapse,
  Container,
  Flex,
  Grid,
  Group,
  Pagination,
  Paper,
  Select,
  Space,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import {
  useDebouncedValue,
  useDisclosure,
  useDocumentTitle,
  useMounted,
} from '@mantine/hooks';

import {
  FilteredSpeciesList,
  SpeciesList,
  Facet,
  performGQLQuery,
  SpeciesListItem,
  queries,
  KV,
  SpeciesListSubmit,
} from '#/api';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';
import { Outlet, useLocation, useParams } from 'react-router';
import {
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
  useQueryState,
} from 'nuqs';

// Icons
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faMagnifyingGlass, faPlus, faSliders, faXmark } from '@fortawesome/free-solid-svg-icons';

import tableClasses from './classes/Table.module.css';

// Table components
import { ThEditable } from './components/Table/ThEditable';
import { TrItem } from './components/Table/TrItem';
import { ThCreate } from './components/Table/ThCreate';

// Local component imports
import { IngestProgress } from '#/components/IngestProgress';
import { Message } from '#/components/Message';
import { getErrorMessage, parseAsFilters } from '#/helpers';
import { SpeciesItemDrawer } from './components/SpeciesItemDrawer';
import { useALA } from '#/helpers/context/useALA';
import { Flags } from './components/Flags';
import { Actions } from './components/Actions';
import { Summary } from './components/Summary';
import { ActiveFilters, FiltersSection } from '#/components/FiltersSection';
import { ThSortable } from './components/Table/ThSortable';
import { Dates } from './components/Dates';
import PageLoader from '#/components/PageLoader';
import { getAccessToken } from '#/helpers/utils/getAccessToken';

// Styles
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

const classificationFields = ['family', 'kingdom', 'vernacularName'];

export function List() {
  const { id } = useParams();
  const [_data, setData] = useState<ListLoaderData | null>(null);
  const [loading, setLoading] = useState(true);

  const [list, setList] = useState<FilteredSpeciesList | null>(null);
  const [meta, setMeta] = useState<SpeciesList | null>(null);

  useDocumentTitle(`ALA Lists | ${meta?.title || 'Loading...'}`);

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

  const location = useLocation();
  const mounted = useMounted();
  const ala = useALA();
  const intl = useIntl();

  // Selection drawer
  const [opened, { open, close }] = useDisclosure();
  const [selected, setSelected] = useState<SpeciesListItem | null>(null);
  
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
  const realPage = page + 1;

  // If we're on the reingest page
  const isReingest = location.pathname.endsWith('reingest');

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
      <Container fluid>
        <Grid>
          <Grid.Col span={12} pb={6}>
            <Flex direction='row' justify='space-between' gap={16}>
              <Stack gap='xs' mb={14}>
                <Title order={4}>{meta?.title}</Title>
                <Summary meta={meta!} mr={-42} />
                {(meta?.tags || []).length > 0 && (
                  <Group mt={4} gap={4}>
                    {meta!.tags.map((tag) => (
                      <Badge variant='dot' radius='md' key={tag}>
                        {tag}
                      </Badge>
                    ))}
                  </Group>
                )}
                {meta?.description && (
                  <Text c='dark-grey-1' size='sm' mt='xs' opacity={0.75}>
                    {meta.description}
                  </Text>
                )}
                <Group mt='sm' gap='xs'>
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
                  onRematch={() => {
                    setRematching(true);
                  }}
                />
              )}
            </Flex>
            <Button
              radius='md'
              leftSection={<FontAwesomeIcon icon={faPlus} />}
              variant='light'
              onClick={handleAddClick}
              title={intl.formatMessage({ id: 'add.species.title', defaultMessage: 'Add a new taxon entry' })}
              aria-label={intl.formatMessage({ id: 'add.species.title', defaultMessage: 'Add a new taxon entry' })}
            >
              <FormattedMessage id='add.species.label' defaultMessage='Add species' />
            </Button>
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
                <Group >
                  <Button 
                    size= 'sm' 
                    leftSection={<FontAwesomeIcon icon={faSliders} fontSize={14}/>}
                    variant='default'
                    radius='md'
                    fw='normal'
                    onClick={toggleFilters}
                    aria-label={intl.formatMessage({ id: 'filters.toggle.label', defaultMessage: 'Show or hide filters section' })}
                    title={intl.formatMessage({ id: 'filters.toggle.label', defaultMessage: 'Show or hide filters section' })}
                  >
                  { hidefilters 
                      ? <FormattedMessage id='filters.hide' defaultMessage='Show Filters' /> 
                      : <FormattedMessage id='filters.show' defaultMessage='Hide Filters' /> 
                  }
                  </Button>
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
                </Group>
              </Grid.Col>
              {/* Filters appear here */}
              {!hidefilters && (
                <Grid.Col span={2} mt={4}>
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
              <Grid.Col span={hidefilters ? 12 : 10}>
                { (totalElements && totalElements > 0) ? (
                  <>
                    <Text size='sm' mb={6} mt={6} className={classes.resultsSummary} component='span'>
                      <FormattedMessage id='results.showing' defaultMessage='Showing' /> {' '}
                      {(realPage - 1) * size + 1}-
                      {Math.min((realPage - 1) * size + size, totalElements || 0)} of {' '}
                      <FormattedNumber value={totalElements || 0} /> {' '}
                      <FormattedMessage id='results.records' defaultMessage='records' />
                      { filters && filters.length > 0 && (
                        <><Space w={5} />–<Space w={2} /></>
                      )}
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
                                id={`classification.${field}`}
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
                      onChange={(value) => setPage(value - 1)}
                      total={totalPages || 9}
                      radius='md'
                      getControlProps={(control) => ({
                        'aria-label': `${control} page`,
                      })}
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