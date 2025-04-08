import { CSSProperties, memo, useCallback, useEffect, useState } from 'react';
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
  Box,
  Checkbox,
} from '@mantine/core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faFilter,
  faMinus,
  faPlus,
  faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { FormattedMessage, FormattedNumber } from 'react-intl';
import { VariableSizeList as List } from 'react-window';

import classes from './FiltersSection.module.css';
import { Facet, KV } from '#/api';

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
      <Box>
        <UnstyledButton
          className={classes.filter}
          onClick={handleClick}
          w='100%'
        >
          <Group justify='space-between'>
            <Text size='sm'>
              <FormattedMessage
                id={value}
                defaultMessage={value}
              />
            </Text>
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
      </Box>
    );
  }
);

export const FacetComponent = memo(
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
    facet.counts.sort((a: { value: string }, b: { value: string }) => a.value.localeCompare(b.value))
    // const itemSize = 38;
    // const height = Math.min(itemCount * itemSize, 300);
    // const height = itemCount * itemSize + 0;

    return (
      <>
        { itemCount > 2 || (facet.counts[0].value !== 'true' && facet.counts[0].value !== 'false') ? (
          // Non-boolean facets
          <Paper 
            className={classes.facetPaper}
            fs="sm" 
            radius={0} 
          >
            <Text size='md' fw='bold' opacity={0.8} mb={4}>
              <FormattedMessage id={facet.key} defaultMessage={facet.key} />
            </Text>
            { facet.counts.map((item, _index) => (
              <Checkbox
                key={item.value}
                size='xs'
                classNames={{
                  body: classes.checkboxBody,
                  inner: classes.checkboxInner,
                  labelWrapper: classes.checkboxLabelWrapper,
                  label: classes.checkboxLabel,
                }}
                style={{ marginBottom: 4 }}
                onChange={(_event) =>
                  onSelect({
                    key: facet.key,
                    value: item.value
                  })
                }
                checked={
                  Boolean(
                    active.find(
                      (activeItem) =>
                      activeItem.key === facet.key &&
                      activeItem.value === item.value
                    )
                  )
                }
                label={
                  <div style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <FormattedMessage id={item.value} defaultMessage={item.value} />
                    <Chip
                      size="xs"
                      checked={Boolean(
                        active.find(
                          (activeItem) =>
                            activeItem.key === facet.key &&
                            activeItem.value === item.value
                        )
                      )}
                      styles={{
                        root: { pointerEvents: 'none' },
                        label: { paddingLeft: 8, paddingRight: 8 },
                        iconWrapper: { display: 'none', width: 0 },
                      }}
                    >
                      <FormattedNumber value={item.count} />
                    </Chip>
                  </div>
                }
              />
            ))}
          </Paper>
        ) : (
          // Boolean facets - assumes 2 values in facet.counts array
          <Paper 
            className={classes.facetPaper}
            fs="sm" 
            radius={0} 
          >
            <Checkbox
              size='xs'
              classNames={{
                body: classes.checkboxBody,
                inner: classes.checkboxInner,
                labelWrapper: classes.checkboxLabelWrapper,
                label: classes.checkboxLabel,
              }}
              onChange={(_event) =>
                onSelect({
                  key: facet.key,
                  value: facet.counts[1].value
                })
              }
              checked={
                Boolean(
                  active.find(
                    (activeItem) =>
                    activeItem.key === facet.key &&
                    activeItem.value === facet.counts[1]?.value 
                  )
                )
              }
              label={
                <div style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <FormattedMessage id={facet.key} />
                  <Chip
                    size="xs"
                    checked={Boolean(
                      active.find(
                        (activeItem) =>
                          activeItem.key === facet.key &&
                          activeItem.value === facet.counts[1]?.value 
                      )
                    )}
                    styles={{
                      root: { pointerEvents: 'none' },
                      label: { paddingLeft: 8, paddingRight: 8 },
                      iconWrapper: { display: 'none', width: 0 },
                    }}
                  >
                    <FormattedNumber value={facet.counts[1]?.count } />
                  </Chip>
                </div>
              }
            />
          </Paper>
        )}
      </>
    );
  }
);

export const FiltersDrawer = memo(
  ({ facets, active, onSelect, onReset }: FiltersDrawerProps) => {
    const [_opened, { open, close }] = useDisclosure(true);
    const [expanded, setExpanded] = useState<string[]>([]);
    // Callback function for facet toggling
    const handleFacetToggle = useCallback((key: string) => {
      
      setExpanded((prevExpanded) =>
        prevExpanded.includes(key)
          ? prevExpanded.filter((item) => item !== key)
          : [...prevExpanded, key]
      );
    }, []);

    useEffect(() => {
      // Set initial expanded facets
      // Expand the first two facets by default
      if (facets.length > 0) {
        setExpanded((prevExpanded) =>
          prevExpanded.length === 0
            ? facets.slice(0, 2).map((facet) => facet.key)
            : prevExpanded
        );
      }
    }, [facets]);

    return (
      <>
        {/* <Drawer.Root
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
            <Drawer.Body p={0}> */}
              <Title order={5}>Filters</Title>
              <Stack gap={4} mt="xs" mb="md" pb={4} style={{ borderBottom: "1px solid var(--mantine-color-default-border)" }}>
                {facets
                  .filter((facet) => facet.counts.length > 0)
                  .sort((a, b) => b.counts.length - a.counts.length)
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
            {/* </Drawer.Body>
          </Drawer.Content>
        </Drawer.Root> */}
        <Flex align='center' gap={14} display="noneX">
          <ActionIcon.Group>
            <ActionIcon
              display="none"
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
