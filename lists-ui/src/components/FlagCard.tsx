import { Checkbox, CheckboxCardProps, Flex, Text } from '@mantine/core';

import classes from './FlagCard.module.css';
import { ListTypeBadge } from './ListTypeBadge';

interface FlagCardProps extends CheckboxCardProps {
  label: string;
  description: string;
  flag: string;
}

export function FlagCard({
  label,
  description,
  flag,
  ...props
}: FlagCardProps) {
  const labelText = (<>
    <strong>{label}</strong>
  </>);
  return (
    <Checkbox.Card {...props} className={classes.root} radius='lg' h='100%'>
      <Flex align='flex-start' w='100%'>
        <Checkbox.Indicator />
        <Flex direction='column' w='100%' ml='sm'>
          <Flex justify='space-between' align='center'>
            <ListTypeBadge listTypeValue={flag} titleText={labelText} iconSide='right' />
          </Flex>
          <Text opacity={0.75} className={classes.description}>
            {description}
          </Text>
        </Flex>
      </Flex>
    </Checkbox.Card>
  );
}
