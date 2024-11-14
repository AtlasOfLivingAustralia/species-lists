import { PropsWithChildren } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faSort,
  faSortDown,
  faSortUp,
} from '@fortawesome/free-solid-svg-icons';
import { ThNoWrap } from './ThNoWrap';
import { TableThProps, UnstyledButton } from '@mantine/core';
import { ThMatch } from './ThMatch';

interface ThSortableProps extends PropsWithChildren<TableThProps> {
  match?: boolean;
  active: boolean;
  dir: 'asc' | 'desc';
  onSort: () => void;
}

export function ThSortable({
  children,
  match,
  dir,
  active,
  onSort,
  ...rest
}: ThSortableProps) {
  const Component = match ? ThMatch : ThNoWrap;
  return (
    <Component {...rest}>
      {children}
      <UnstyledButton onClick={onSort}>
        <FontAwesomeIcon
          icon={!active ? faSort : dir === 'desc' ? faSortDown : faSortUp}
          fontSize={12}
          style={{
            opacity: 0.4,
            marginLeft: 10,
          }}
        />
      </UnstyledButton>
    </Component>
  );
}
