/* eslint-disable @typescript-eslint/no-explicit-any */
import { InputSpeciesList, KV, SpeciesList } from '#/api';
import { Group, Table, Textarea, TextInput, Title } from '@mantine/core';

import { FormattedMessage } from 'react-intl';
import { useForm } from '@mantine/form';
import { useEffect } from 'react';
import { useRouteLoaderData } from 'react-router';

interface ListLoaderData {
  meta: SpeciesList;
}

interface SpeciesItemCreateProps {
  onItemUpdated: (item: InputSpeciesList | null) => void;
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

export function Create({ onItemUpdated }: SpeciesItemCreateProps) {
  const { meta } = useRouteLoaderData('list') as ListLoaderData;

  const classificationForm = useForm({
    initialValues: classificationFields.reduce(
      (prev, cur) => ({
        ...prev,
        [cur]: '',
      }),
      {}
    ),
  });

  const propertiesForm = useForm({
    initialValues: meta.fieldList.reduce(
      (prev, field) => ({
        ...prev,
        [field]: '',
      }),
      {}
    ),
  });

  // Trigger the item updated callback if the form has been changed
  useEffect(() => {
    const classificationValues = Object.values(
      classificationForm.values
    ) as string[];

    // Ensure at lease one of the classification fields has a value in it
    if (classificationValues.join('').length < 1) {
      onItemUpdated(null);
      return;
    }

    if (classificationForm.isDirty() || propertiesForm.isDirty()) {
      onItemUpdated({
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
      <Table withRowBorders striped={false}>
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
          {meta.fieldList.map((field) => (
            <Table.Tr key={field}>
              <Table.Td>{field}</Table.Td>
              <Table.Td>
                <Textarea
                  aria-label={field}
                  key={propertiesForm.key(field)}
                  {...propertiesForm.getInputProps(field)}
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
