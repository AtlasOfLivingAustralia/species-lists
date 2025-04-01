/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  Badge,
  Box,
  Button,
  Center,
  Container,
  Flex,
  Grid,
  Group,
  Pagination,
  Select,
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
import { FormattedMessage, FormattedNumber } from 'react-intl';
import { Outlet, useLocation, useParams } from 'react-router';
import {
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
  useQueryState,
} from 'nuqs';


// Icons
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';

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
import { FiltersDrawer } from '#/components/FiltersDrawer';
import { ThSortable } from './components/Table/ThSortable';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Dates } from './components/Dates';
import PageLoader from '#/components/PageLoader';
import { getAccessToken } from '#/helpers/utils/getAccessToken';

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

const toKV = (data: { [key: string]: string }) =>
  Object.entries(data).map(([key, value]) => ({ key, value }));

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
    parseAsInteger.withDefault(10)
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

  const handleSizeChange = (newSize: string | null) => {
    const newSizeInt = parseInt(newSize || '10', 10);

    // Ensure we don't try to query beyond what exists
    if (realPage * newSizeInt > totalElements) {
      setPage(Math.floor(totalElements / newSizeInt));
    }

    setSize(newSizeInt);
  };

  // Retry handler
  const handleRetry = useCallback(() => {
    setPage(0);
    setSize(10);
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
    return <Message title="An error occurred" subtitle={getErrorMessage(error)} />;
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
          <Grid.Col span={12} pb='md'>
            <Flex direction='row' justify='space-between' gap={16}>
              <Stack gap='xs'>
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
          </Grid.Col>
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
          {isReingest ? (
            <Grid.Col span={12}>
              <Outlet />
            </Grid.Col>
          ) : (
            <>
              <Grid.Col span={12}>
                <Group justify='space-between'>
                  <Group>
                    <Button
                      radius='md'
                      leftSection={<FontAwesomeIcon icon={faPlus} />}
                      variant='light'
                      onClick={handleAddClick}
                    >
                      Add species
                    </Button>
                    <Select
                      disabled={hasError}
                      w={110}
                      value={size?.toString()}
                      onChange={handleSizeChange}
                      data={['10', '20', '40'].map((value) => ({
                        label: `${value} items`,
                        value,
                      }))}
                      aria-label='Select number of results'
                    />
                    <TextInput
                      disabled={hasError}
                      defaultValue={searchDebounced}
                      onChange={(event) => {
                        setSearch(event.currentTarget.value);
                      }}
                      placeholder='Search within list'
                      w={200}
                    />
                  </Group>
                  <Group>
                    <FiltersDrawer
                      active={toKV(Object.fromEntries((filters || []).map(({ key, value }) => [key, value])))}
                      facets={facets}
                      onSelect={handleFilterClick}
                      onReset={() => setFilters([])}
                    />
                    <Text opacity={0.75} size='sm'>
                      {(realPage - 1) * size + 1}-
                      {Math.min(
                        (realPage - 1) * size + size,
                        totalElements || 0
                      )}{' '}
                      of <FormattedNumber value={totalElements} /> records
                    </Text>
                  </Group>
                </Group>
              </Grid.Col>
              <Grid.Col span={12}>
                <Box style={{ overflowX: 'auto' }}>
                  {error ? (
                    <Message
                      title='An error occured'
                      subtitle={getErrorMessage(error)}
                      icon={<StopIcon size={18} />}
                      action='Retry'
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
                            <FormattedMessage
                              id='suppliedName'
                              defaultMessage='Supplied name'
                            />
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
                </Box>
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
            </>
          )}
        </Grid>
      </Container>
    </>
  );
}

export default List;