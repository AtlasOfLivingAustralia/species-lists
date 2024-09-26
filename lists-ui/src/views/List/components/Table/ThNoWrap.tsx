import { PropsWithChildren } from 'react';
import { Flex, Table, TableThProps } from '@mantine/core';

export function ThNoWrap({
  children,
  ...rest
}: PropsWithChildren<TableThProps>) {
  return (
    <Table.Th {...rest} style={{ ...rest.style, textWrap: 'nowrap' }}>
      <Flex align='center'>{children}</Flex>
    </Table.Th>
  );
}
