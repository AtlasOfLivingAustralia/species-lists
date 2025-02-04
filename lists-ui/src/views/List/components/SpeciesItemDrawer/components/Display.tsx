/* eslint-disable @typescript-eslint/no-explicit-any */
import { useRouteLoaderData } from 'react-router';
import { SpeciesList, SpeciesListItem } from '#/api';
import { useState } from 'react';
import { Group, Switch, Table, Textarea, Title } from '@mantine/core';

import classes from './Display.module.css';
import displayClasses from './TextArea.module.css';
import { FormattedMessage } from 'react-intl';

interface ListLoaderData {
  meta: SpeciesList;
}

interface SpeciesItemDisplayProps {
  item: SpeciesListItem;
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

export function Display({ item }: SpeciesItemDisplayProps) {
  const { meta } = useRouteLoaderData('list') as ListLoaderData;
  const [expandedTaxonomy, setExpandedTaxonomy] = useState<boolean>(false);
  const expandedStyle = {
    padding: 0,
    width: expandedTaxonomy ? '33%' : 0,
  };

  return (
    <>
      <Group justify='space-between'>
        <Title order={5}>Taxonomy</Title>
        <Switch
          checked={expandedTaxonomy}
          onChange={(event) => setExpandedTaxonomy(event.currentTarget.checked)}
          size='xs'
          label='Show supplied values'
          labelPosition='left'
        />
      </Group>
      <Table className={classes.table} withRowBorders striped={false}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Rank</Table.Th>
            <Table.Th pl={23}>Matched</Table.Th>
            <Table.Th style={expandedStyle}>Supplied</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {classificationFields.map((field) => (
            <Table.Tr key={field} h={50}>
              <Table.Td>
                <FormattedMessage id={`classification.${field}`} />
              </Table.Td>
              <Table.Td pl={23}>
                {(item.classification as any)?.[field]}
              </Table.Td>
              <Table.Td style={expandedStyle}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    overflow: 'hidden',
                    height: 50,
                  }}
                >
                  {(item as any)?.[field]}
                </div>
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
                  disabled
                  classNames={displayClasses}
                  autosize
                  value={
                    item.properties.find(({ key }) => key === field)?.value ||
                    ''
                  }
                />
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </>
  );
}
