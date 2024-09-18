import { CSSProperties, Fragment, memo, useCallback, useState } from 'react';
import { useDisclosure } from '@mantine/hooks';
import {
  Drawer,
  ScrollArea,
  Flex,
  Title,
  Stack,
  Text,
  Group,
  UnstyledButton,
  Paper,
  Chip,
  Collapse,
  ActionIcon,
} from '@mantine/core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faFilter,
  faMinus,
  faPlus,
  faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { FormattedMessage, FormattedNumber } from 'react-intl';
import { FixedSizeList as List } from 'react-window';

import classes from './FiltersDrawer.module.css';

interface KV {
  key: string;
  value: string;
}

interface Facet {
  key: string;
  counts: { value: string; count: number }[];
}

interface FiltersDrawerProps {
  facets: Facet[];
  active: KV[];
  onSelect: (item: KV) => void;
  onReset: () => void;
}

const CountItem = memo(
  ({
    style,
    value,
    count,
    facetKey,
    isChecked,
    onSelect,
  }: {
    style: CSSProperties;
    value: string;
    count: number;
    facetKey: string;
    isChecked: boolean;
    onSelect: (item: KV) => void;
  }) => {
    // Click callback
    const handleClick = useCallback(() => {
      onSelect({ key: facetKey, value });
    }, [onSelect, facetKey, value]);

    return (
      <div style={style}>
        <UnstyledButton
          className={classes.filter}
          onClick={handleClick}
          w='100%'
        >
          <Group justify='space-between'>
            <Text size='sm'>{value}</Text>
            <Chip
              checked={isChecked}
              size='xs'
              styles={{
                root: { pointerEvents: 'none' },
                label: { paddingLeft: 8, paddingRight: 8 },
              }}
            >
              <FormattedNumber value={count} />
            </Chip>
          </Group>
        </UnstyledButton>
      </div>
    );
  }
);

const FacetComponent = memo(
  ({
    facet,
    isExpanded,
    handleFacetToggle,
    active,
    onSelect,
  }: {
    facet: Facet;
    isExpanded: boolean;
    handleFacetToggle: (key: string) => void;
    active: KV[];
    onSelect: (item: KV) => void;
  }) => {
    const handleToggle = useCallback(() => {
      handleFacetToggle(facet.key);
    }, [handleFacetToggle, facet.key]);

    // Calculate item counts & sizes for
    const itemCount = facet.counts.length;
    const itemSize = 41;
    const height = Math.min(itemCount * itemSize, 300);

    return (
      <Fragment>
        <Paper className={classes.heading}>
          <Group justify='space-between'>
            <Text size='lg' fw='bold' opacity={0.8}>
              <FormattedMessage id={facet.key} defaultMessage={facet.key} />
            </Text>
            <ActionIcon
              variant='subtle'
              color='dark'
              size='sm'
              onClick={handleToggle}
              aria-label={`Toggle filters for ${facet.key}`}
            >
              <FontAwesomeIcon icon={isExpanded ? faMinus : faPlus} />
            </ActionIcon>
          </Group>
        </Paper>
        <Collapse in={isExpanded} mb='md'>
          <List
            height={height}
            itemCount={itemCount}
            itemSize={itemSize}
            width='100%'
          >
            {({ index, style }: { index: number; style: CSSProperties }) => {
              const { value, count } = facet.counts[index];
              return (
                <CountItem
                  key={value}
                  style={style}
                  value={value}
                  count={count}
                  facetKey={facet.key}
                  isChecked={Boolean(
                    active.find(
                      (activeItem) =>
                        activeItem.key === facet.key &&
                        activeItem.value === value
                    )
                  )}
                  onSelect={onSelect}
                />
              );
            }}
          </List>
        </Collapse>
      </Fragment>
    );
  }
);

export const FiltersDrawer = memo(
  ({ facets, active, onSelect, onReset }: FiltersDrawerProps) => {
    const [opened, { open, close }] = useDisclosure(false);
    const [expanded, setExpanded] = useState<string[]>([]);

    // Callback function for facet toggling
    const handleFacetToggle = useCallback((key: string) => {
      setExpanded((prevExpanded) =>
        prevExpanded.includes(key)
          ? prevExpanded.filter((item) => item !== key)
          : [...prevExpanded, key]
      );
    }, []);

    return (
      <>
        <Drawer.Root
          offset={16}
          radius='lg'
          size='md'
          opened={opened}
          onClose={close}
          position='left'
          scrollAreaComponent={ScrollArea.Autosize}
          aria-label='Filters dialog'
          role='dialog'
        >
          <Drawer.Overlay />
          <Drawer.Content
            style={{ overflowX: 'hidden' }}
            aria-label='Filters dialog'
          >
            <Drawer.Header>
              <Flex justify='space-between' align='center' w='100%'>
                <Title order={4}>Filters</Title>
                <Drawer.CloseButton
                  radius='lg'
                  mx={0}
                  aria-label='Close filters'
                />
              </Flex>
            </Drawer.Header>
            <Drawer.Body p={0}>
              <Stack gap='xs'>
                {facets
                  .filter((facet) => facet.counts.length > 0)
                  .map((facet) => (
                    <FacetComponent
                      key={facet.key}
                      facet={facet}
                      isExpanded={expanded.includes(facet.key)}
                      handleFacetToggle={handleFacetToggle}
                      active={active}
                      onSelect={onSelect}
                    />
                  ))}
              </Stack>
            </Drawer.Body>
          </Drawer.Content>
        </Drawer.Root>
        <Flex align='center' gap={14}>
          <ActionIcon.Group>
            <ActionIcon
              size={36}
              radius='md'
              variant='default'
              onClick={open}
              aria-label='Open filters menu'
            >
              <FontAwesomeIcon icon={faFilter} fontSize={14} />
            </ActionIcon>
            {active.length > 0 ? (
              <ActionIcon
                size={36}
                radius='md'
                variant='default'
                onClick={onReset}
                aria-label='Reset filets'
              >
                <FontAwesomeIcon icon={faXmark} fontSize={14} />
              </ActionIcon>
            ) : null}
            <ActionIcon
              variant='default'
              radius='md'
              w={70}
              h={36}
              style={{ pointerEvents: 'none' }}
            >
              <Text size='sm' opacity={0.75}>
                {active.length > 0 ? active.length : 'No'} filte
                {active.length === 1 ? 'r' : 'rs'}
              </Text>
            </ActionIcon>
          </ActionIcon.Group>
        </Flex>
      </>
    );
  }
);
