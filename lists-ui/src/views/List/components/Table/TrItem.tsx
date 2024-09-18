import { MantineStyleProp, Table, TableTrProps } from '@mantine/core';
import { SpeciesListItem } from '#/api';
import find from 'lodash-es/find';

interface TrItemProps extends TableTrProps {
  row: SpeciesListItem;
  fields: string[];
  classification: string[];
  editing: boolean;
}

const tdStyles: MantineStyleProp = {
  maxWidth: 500,
  whiteSpace: 'nowrap',
  textOverflow: 'ellipsis',
  overflow: 'hidden',
};

export function TrItem({
  row,
  fields,
  classification,
  editing,
  ...rest
}: TrItemProps) {
  return (
    <Table.Tr
      style={{
        maxWidth: '100%',
        whiteSpace: 'nowrap',
      }}
      {...rest}
    >
      <Table.Td width='auto'>{row.scientificName}</Table.Td>
      <Table.Td width='auto'>{row.classification.scientificName}</Table.Td>
      {fields.map((key) => (
        <Table.Td key={key} style={tdStyles}>
          {find(row.properties, { key })?.value}
        </Table.Td>
      ))}
      {editing && <Table.Td />}
      {classification.map((key) => (
        <Table.Td key={key} style={tdStyles}>
          {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
          {(row.classification as any)[key]}
        </Table.Td>
      ))}
    </Table.Tr>
  );
}
