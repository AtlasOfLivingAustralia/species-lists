import { PropsWithChildren } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faLink } from '@fortawesome/free-solid-svg-icons';
import { ThNoWrap } from './ThNoWrap';
import { TableThProps } from '@mantine/core';

export function ThMatch({
  children,
  ...rest
}: PropsWithChildren<TableThProps>) {
  return (
    <ThNoWrap {...rest}>
      <FontAwesomeIcon
        icon={faLink}
        fontSize={12}
        style={{
          opacity: 0.4,
          marginRight: 10,
        }}
      />
      {children}
    </ThNoWrap>
  );
}
