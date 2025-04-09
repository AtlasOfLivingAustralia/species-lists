import { CSSProperties, memo, useCallback, useEffect, useMemo, useState } from 'react';
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
import { ListTypeBadge } from './ListTypeBadge';

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

// Helper function to render the entire Checkbox with its label
const renderCheckbox = (
  key: string,
  countItem: { value: string; count: number } | undefined,
  isChecked: boolean,
  isBooleanFacet: boolean,
  onChange: () => void // Accept the specific onChange handler
) => {
  if (!countItem) return null; // Handle case where countItem might be undefined

  return (
    <Checkbox
      key={key} // Use the provided key
      size='xs'
      classNames={{
        root: !isBooleanFacet ? classes.checkboxRoot : undefined,
        body: classes.checkboxBody,
        inner: classes.checkboxInner,
        labelWrapper: classes.checkboxLabelWrapper,
        label: classes.checkboxLabel,
      }}
      onChange={onChange} // Use the provided onChange handler
      checked={isChecked}
      label={
        // The label structure remains the same
        <Paper className={classes.checkboxPaper}>
          <ListTypeBadge listTypeValue={key} iconSide="right"/>
          {/* <FormattedMessage id={key} defaultMessage={key} /> */}
          <Chip
            size="xs"
            checked={isChecked}
            styles={{
              root: { pointerEvents: 'none' },
              label: { paddingLeft: 8, paddingRight: 8 },
              iconWrapper: { display: 'none', width: 0 },
            }}
          >
            <FormattedNumber value={countItem.count} />
          </Chip>
        </Paper>
      }
    />
  );
};


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

    // Create a sorted copy of counts
    const sortedCounts = useMemo(() =>
      [...facet.counts].sort((a, b) => a.value.localeCompare(b.value)),
      [facet.counts]
    );

    const itemCount = sortedCounts.length;

    // Determine if it's a boolean facet
    const isBooleanFacet = itemCount <= 2 &&
        (sortedCounts[0]?.value === 'true' || sortedCounts[0]?.value === 'false');

    // Helper to check if a value is active
    const isValueActive = useCallback((value: string | undefined) => {
      if (value === undefined) return false;
      return Boolean(active.find(
        (activeItem) => activeItem.key === facet.key && activeItem.value === value
      ));
    }, [active, facet.key]);

    // Specific onChange handler for boolean facet
    const handleBooleanChange = useCallback(() => {
      const booleanItem = sortedCounts[1]; // Assumes second item is the one to toggle
      if (booleanItem) {
        onSelect({ key: facet.key, value: booleanItem.value });
      }
    }, [onSelect, facet.key, sortedCounts]);

    // Specific onChange handler factory for non-boolean items
    const handleItemChange = useCallback((itemValue: string) => () => {
        onSelect({ key: facet.key, value: itemValue });
    }, [onSelect, facet.key]);

    return (
      <Paper
        className={classes.facetPaper}
        fs="sm"
        radius={0}
      >
        {/* Render header only for non-boolean facets */}
        {!isBooleanFacet && (
          <Group justify='space-between'>
            <Text size='md' fw='bold' opacity={0.8} mb={4}>
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
        )}

        {/* Render checkboxes using the helper */}
        {isBooleanFacet ? (
          // --- Boolean Facet Rendering ---
          (() => {
            const booleanItem = sortedCounts[1];
            const isChecked = isValueActive(booleanItem?.value);
            return renderCheckbox(
              facet.key, // Key for the single boolean checkbox
              booleanItem,
              isChecked,
              isBooleanFacet,
              handleBooleanChange // Pass the specific handler
            );
          })()
        ) : (
          // --- Non-boolean Facet Rendering ---
          <Collapse in={isExpanded} >
            {sortedCounts.map((item) => {
              const isChecked = isValueActive(item.value);
              return renderCheckbox(
                item.value, // Key is the item value
                item,
                isChecked,
                isBooleanFacet,
                handleItemChange(item.value) // Pass the specific handler for this item
              );
            })}
          </Collapse>
        )}
      </Paper>
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
        <Text size='md' fw='bold'><FormattedMessage id='filters.title' defaultMessage='Filters' /></Text>
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
