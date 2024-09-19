import { Checkbox, CheckboxCardProps, Flex, Text } from '@mantine/core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

import classes from './FlagCard.module.css';

interface FlagCardProps extends CheckboxCardProps {
  label: string;
  description: string;
  icon: IconProp;
}

export function FlagCard({
  label,
  description,
  icon,
  ...props
}: FlagCardProps) {
  return (
    <Checkbox.Card {...props} className={classes.root} radius='lg' h='100%'>
      <Flex align='flex-start' w='100%'>
        <Checkbox.Indicator />
        <Flex direction='column' w='100%' ml='sm'>
          <Flex justify='space-between' align='center'>
            <Text className={classes.label}>{label}</Text>
            <FontAwesomeIcon icon={icon} fontSize={22} opacity={0.75} />
          </Flex>
          <Text opacity={0.75} className={classes.description}>
            {description}
          </Text>
        </Flex>
      </Flex>
    </Checkbox.Card>
  );
}
