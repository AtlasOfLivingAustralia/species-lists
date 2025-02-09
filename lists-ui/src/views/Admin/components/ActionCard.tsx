import { ReactNode } from 'react';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Group,
  Paper,
  PolymorphicComponentProps,
  Stack,
  Text,
  UnstyledButton,
  UnstyledButtonProps,
} from '@mantine/core';

import classes from './ActionCard.module.css';

interface ActionCardProps
  extends PolymorphicComponentProps<'button', UnstyledButtonProps> {
  title: string;
  description: ReactNode;
  icon: IconProp;
}

export function ActionCard({
  title,
  description,
  icon,
  disabled,
  ...rest
}: ActionCardProps) {
  return (
    <UnstyledButton
      {...rest}
      disabled={disabled}
      className={!disabled ? classes.card : classes.disabled}
      w='100%'
      h='100%'
    >
      <Paper h='100%' p='lg' shadow='sm' radius='lg' withBorder>
        <Stack>
          <Group gap='lg' align='center'>
            <FontAwesomeIcon icon={icon} fontSize={24} />
            <Text size='xl' fw='bold'>
              {title}
            </Text>
          </Group>
          <Text c='dimmed'>{description}</Text>
        </Stack>
      </Paper>
    </UnstyledButton>
  );
}
