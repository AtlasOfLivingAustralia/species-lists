import { SpeciesListItem } from '#/api';
import { getStyleForTaxon } from '#/helpers/utils/formatName';
import { Anchor, MantineStyleProp, Table, TableTrProps } from '@mantine/core';
import find from 'lodash-es/find';
import { useIntl } from 'react-intl';

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

  const intl = useIntl();

  return (
    <Table.Tr
      style={{
        maxWidth: '100%',
        whiteSpace: 'nowrap',
      }}
      {...rest}
    >
      <Table.Td width='auto'>{row.suppliedName || row.scientificName}</Table.Td>
      <Table.Td width='auto'>
        {row.classification?.scientificName ? (
          <Anchor
            fs={getStyleForTaxon(
                  row.classification?.rank,
                  row.classification?.rankID,
                  'italic'
                )}
            href={`${import.meta.env.VITE_ALA_BIE_SPECIES}/${
              row.classification?.taxonConceptID
            }`}
            target='_blank'
          >
            {row.classification?.scientificName}
          </Anchor>
        ) : (
          ''
        )}
      </Table.Td>
      {fields.map((key) => (
        <Table.Td key={key} style={tdStyles}>
          {find(row.properties, { key })?.value}
        </Table.Td>
      ))}
      {editing && <Table.Td />}
      {classification.map((key) => (
        <Table.Td key={key} style={tdStyles}>
          {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
          {key === "matchType" 
            ? intl.formatMessage({ id: `classification.${(row.classification as any)?.[key]}` })
            : (row.classification as any)?.[key]}
        </Table.Td>
      ))}
    </Table.Tr>
  );
}
