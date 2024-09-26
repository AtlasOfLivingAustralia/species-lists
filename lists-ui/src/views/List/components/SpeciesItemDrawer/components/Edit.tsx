/* eslint-disable @typescript-eslint/no-explicit-any */
import { InputSpeciesList, KV, SpeciesList, SpeciesListItem } from '#/api';
import { Group, Table, Textarea, TextInput, Title } from '@mantine/core';

import classes from './Edit.module.css';
import { FormattedMessage } from 'react-intl';
import { useForm } from '@mantine/form';
import { useEffect } from 'react';
import { useRouteLoaderData } from 'react-router-dom';

interface ListLoaderData {
  meta: SpeciesList;
}

interface SpeciesItemEditProps {
  item: SpeciesListItem;
  onItemUpdated: (item: InputSpeciesList) => void;
}

const classificationFields = [
  'scientificName',
  'vernacularName',
  'kingdom',
  'phylum',
  'classs',
  'order',
  'family',
  'genus',
];

export function Edit({ item, onItemUpdated }: SpeciesItemEditProps) {
  const { meta } = useRouteLoaderData('list') as ListLoaderData;

  const classificationForm = useForm({
    initialValues: classificationFields.reduce(
      (prev, cur) => ({
        ...prev,
        [cur]: (item as any)[cur] || '',
      }),
      {}
    ),
  });

  const propertiesForm = useForm({
    initialValues: item.properties.reduce(
      (prev, { key, value }) => ({
        ...prev,
        [key]: value || '',
      }),
      {}
    ),
  });

  // Trigger the item updated callback if the form has been changed
  useEffect(() => {
    if (classificationForm.isDirty() || propertiesForm.isDirty()) {
      onItemUpdated({
        id: item.id,
        speciesListID: meta.id,
        ...classificationForm.values,
        properties: Object.entries(propertiesForm.values).map(
          ([key, value]) => ({
            key,
            value,
          })
        ) as KV[],
      } as InputSpeciesList);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [classificationForm.values, propertiesForm.values]);

  return (
    <>
      <Group justify='space-between'>
        <Title order={5}>Taxonomy</Title>
      </Group>
      <Table className={classes.table} withRowBorders striped={false}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Rank</Table.Th>
            <Table.Th pl={23}>Supplied</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {classificationFields.map((field) => (
            <Table.Tr key={field}>
              <Table.Td>
                <FormattedMessage id={`classification.${field}`} />
              </Table.Td>
              <Table.Td>
                <TextInput
                  key={classificationForm.key(field)}
                  aria-label={field}
                  {...classificationForm.getInputProps(field)}
                />
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      <Title order={5} mt='md'>
        Properties
      </Title>
      <Table withRowBorders striped={false}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Property</Table.Th>
            <Table.Th pl={23}>Value</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {item.properties.map(({ key }) => (
            <Table.Tr key={key}>
              <Table.Td>{key}</Table.Td>
              <Table.Td>
                <Textarea
                  aria-label={key}
                  key={propertiesForm.key(key)}
                  {...propertiesForm.getInputProps(key)}
                  autosize
                />
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </>
  );
}
