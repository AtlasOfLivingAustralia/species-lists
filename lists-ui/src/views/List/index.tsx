/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useState } from 'react';

import {
  Badge,
  Box,
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
  useDebouncedState,
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
import { useLoaderData, useParams } from 'react-router-dom';

// Icons
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';

import tableClasses from './classes/Table.module.css';

// Table components
import { ThEditable } from './components/Table/ThEditable';
import { TrItem } from './components/Table/TrItem';
import { ThCreate } from './components/Table/ThCreate';

// Local component imports
import { Message } from '#/components/Message';
import { getErrorMessage } from '#/helpers';
import { SpeciesItemDrawer } from './components/SpeciesItemDrawer';
import { useALA } from '#/helpers/context/useALA';
import { Flags } from './components/Flags';
import { Actions } from './components/Actions';
import { Summary } from './components/Summary';
import { FiltersDrawer } from '#/components/FiltersDrawer';
import { ThSortable } from './components/Table/ThSortable';

interface ListLoaderData {
  meta: SpeciesList;
  list: FilteredSpeciesList;
  facets: Facet[];
}

const classificationFields = ['family', 'kingdom', 'vernacularName'];

const toKV = (data: { [key: string]: string }) =>
  Object.entries(data).map(([key, value]) => ({ key, value }));

export function Component() {
  // Component data
  const {
    meta: rawMeta,
    list: loaderList,
    facets,
  } = useLoaderData() as ListLoaderData;
  const [list, setList] = useState<FilteredSpeciesList>(loaderList);
  const [meta, setMeta] = useState<SpeciesList>(rawMeta);

  useDocumentTitle(`ALA Lists | ${meta.title}`);

  const [page, setPage] = useState<number>(0);
  const [size, setSize] = useState<number>(10);
  const [searchQuery, setSearch] = useDebouncedState('', 300);
  const [filters, setFilters] = useState<{ [key: string]: string }>({});
  const [error, setError] = useState<Error | null>(null);
  const [refresh, setRefresh] = useState<boolean>(false);
  const [editing, setEditing] = useState<boolean>(false);

  // Sorting state
  const [sort, setSort] = useState<string>('scientificName');
  const [dir, setDir] = useState<'asc' | 'desc'>('asc');

  const mounted = useMounted();
  const params = useParams();
  const ala = useALA();

  // Selection drawer
  const [opened, { open, close }] = useDisclosure();
  const [selected, setSelected] = useState<SpeciesListItem>(list.content[0]);

  // Destructure results & calculate the real page offset
  const { totalElements, totalPages } = list;
  const realPage = page + 1;

  // Update the search query
  useEffect(() => {
    async function runQuery() {
      try {
        const { list: updatedList } = await performGQLQuery(
          queries.QUERY_LISTS_GET,
          {
            speciesListID: params.id,
            searchQuery,
            page,
            size,
            filters: toKV(filters),
            isPrivate: false,
            sort,
            dir,
          },
          ala.token
        );

        setError(null);
        setList(updatedList);
      } catch (error) {
        setError(error as Error);
      }
    }

    if (mounted) runQuery();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, filters, searchQuery, refresh, sort, dir]);

  // Keep the current page in check
  useEffect(() => {
    if (totalPages && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages]);

  const handleSortClick = useCallback(
    (newSort: string) => {
      if (sort === newSort) {
        setDir(dir === 'asc' ? 'desc' : 'asc');
      } else {
        setSort(newSort);
        setDir('desc');
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
      setList({
        ...list,
        content: list.content.map((orig) =>
          orig.id === item.id ? item : orig
        ),
      });
    },
    [list]
  );

  // Item edit handler
  const handleItemDeleted = useCallback(
    (id: string) => {
      close();
      setList({
        ...list,
        content: list.content.filter((item) => item.id !== id),
      });
    },
    [list]
  );

  // Field deletion handler
  const handleFieldCreated = useCallback(
    (field: string, defaultValue?: string) => {
      setMeta({
        ...meta,
        fieldList: [...meta.fieldList, field],
      });

      // Update list properties to reflect new field
      setList({
        ...list,
        content: list.content.map((item) => ({
          ...item,
          properties: [
            ...item.properties,
            { key: field, value: defaultValue || '' },
          ],
        })),
      });
    },
    [meta, list]
  );

  // Field deletion handler
  const handleFieldDeleted = useCallback(
    (deletedField: string) => {
      setMeta({
        ...meta,
        fieldList: meta.fieldList.filter((field) => field !== deletedField),
      });

      // Update list properties to reflect new item name
      setList({
        ...list,
        content: list.content.map((item) => ({
          ...item,
          properties: item.properties.filter(({ key }) => key !== deletedField),
        })),
      });
    },
    [meta, list]
  );

  // Field deletion handler
  const handleFieldRenamed = useCallback(
    (from: string, to: string) => {
      // Update new list meta to update renamed field
      setMeta({
        ...meta,
        fieldList: meta.fieldList.map((field) => (field === from ? to : field)),
      });

      // Update list properties to reflect new item name
      setList({
        ...list,
        content: list.content.map((item) => ({
          ...item,
          properties: item.properties.map(({ key, value }) =>
            key === from ? { key: to, value } : { key, value }
          ),
        })),
      });
    },
    [meta, list]
  );

  const handleRowClick = useCallback((item: SpeciesListItem) => {
    setSelected(item);
    open();
  }, []);

  const handleFilterClick = useCallback(
    (filter: KV) => {
      if (filters[filter.key] === filter.value) {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { [filter.key]: _, ...rest } = filters;
        setFilters(rest);
      } else {
        setFilters({ ...filters, [filter.key]: filter.value });
      }
    },
    [filters]
  );

  const handleListMetaUpdated = useCallback(
    (item: SpeciesListSubmit) => {
      setMeta({
        ...meta,
        ...item,
      });
    },
    [meta]
  );

  const hasError = Boolean(error);

  return (
    <>
      <SpeciesItemDrawer
        opened={opened}
        item={selected}
        onClose={close}
        onEdited={handleItemEdited}
        onDeleted={handleItemDeleted}
      />
      <Container fluid>
        <Grid>
          <Grid.Col span={12} pb='md'>
            <Flex direction='row' justify='space-between' gap={16}>
              <Stack gap='xs'>
                <Title order={4}>{meta.title}</Title>
                <Summary meta={meta} mr={-42} />
                {(meta.tags || []).length > 0 && (
                  <Group mt={4} gap={4}>
                    {meta.tags.map((tag) => (
                      <Badge variant='dot' radius='md' key={tag}>
                        {tag}
                      </Badge>
                    ))}
                  </Group>
                )}
                {meta.description && (
                  <Text c='dark-grey-1' size='sm' mt='xs' opacity={0.75}>
                    {meta.description}
                  </Text>
                )}
                <Group mt='sm'>
                  <Flags meta={meta} />
                </Group>
              </Stack>
              <Actions
                meta={meta}
                editing={editing}
                onEditingChange={setEditing}
                onMetaEdited={handleListMetaUpdated}
                onRematched={() => setRefresh(!refresh)}
              />
            </Flex>
          </Grid.Col>
          <Grid.Col span={12}>
            <Group justify='space-between'>
              <Group>
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
                  defaultValue={searchQuery}
                  onChange={(event) => {
                    setSearch(event.currentTarget.value);
                  }}
                  placeholder='Search within list'
                  w={200}
                />
              </Group>
              <Group>
                <FiltersDrawer
                  active={toKV(filters)}
                  facets={facets}
                  onSelect={handleFilterClick}
                  onReset={() => setFilters({})}
                />
                <Text opacity={0.75} size='sm'>
                  {(realPage - 1) * size + 1}-
                  {Math.min((realPage - 1) * size + size, totalElements || 0)}{' '}
                  of <FormattedNumber value={totalElements} /> total records
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
                      {meta.fieldList.map((field) => (
                        <ThEditable
                          key={field}
                          id={meta.id}
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
                          id={meta.id}
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
                          <FormattedMessage id={`classification.${field}`} />
                        </ThSortable>
                      ))}
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {list.content.map((item) => (
                      <TrItem
                        key={item.id}
                        row={item}
                        fields={meta.fieldList}
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
        </Grid>
      </Container>
    </>
  );
}

Object.assign(Component, { displayName: 'List' });
