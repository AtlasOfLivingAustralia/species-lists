import { Checkbox, CheckboxCardProps, Flex, List, Text } from '@mantine/core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

import classes from './FlagCard.module.css';
import { ListTypeBadge } from './ListTypeBadge';

interface FlagCardProps extends CheckboxCardProps {
  label: string;
  description: string;
  icon: IconProp;
  flag: string;
}

export function FlagCard({
  label,
  description,
  icon,
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
