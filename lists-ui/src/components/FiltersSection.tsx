import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Stack,
  Text,
  Group,
  Paper,
  Chip,
  Collapse,
  ActionIcon,
  Checkbox,
  Tooltip,
  ThemeIcon,
  Box,
} from '@mantine/core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faClose,
  faDeleteLeft,
  faInfoCircle,
  faMinus,
  faPlus,
} from '@fortawesome/free-solid-svg-icons';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';

import classes from './FiltersSection.module.css';
import { Facet, KV } from '#/api';
import { ListTypeBadge } from './ListTypeBadge';

interface FiltersDrawerProps {
  facets: Facet[];
  active: KV[];
  onSelect: (item: KV) => void;
  onReset: () => void;
}

const BOOLEAN_FACETS = ['isAuthoritative', 'isSDS', 'isBIE', 'hasRegion'];

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
          <Chip
            size="xs"
            checked={isChecked}
            classNames={{
              root: classes.countsChipRoot,
              label: classes.countsChipLabel,
              iconWrapper: classes.countsChipIconWrapper,
            }}
          >
            <FormattedNumber value={countItem.count} />
          </Chip>
        </Paper>
      }
    />
  );
};

function InfoTooltip({ tooltipText }: { tooltipText: string }) {
  return (
    <Tooltip label={tooltipText} withArrow position="top">
      <ThemeIcon size="sm" variant="transparent" color="main" opacity={0.8} style={{ cursor: 'pointer' }}>
        <FontAwesomeIcon icon={faInfoCircle} size="sm" />
      </ThemeIcon>
    </Tooltip>
  );
}

export const FacetComponent = memo(
  ({
    facet,
    isExpanded,
    handleFacetToggle,
    active,
    onSelect,
    isShowFlagLabel,
  }: {
    facet: Facet;
    isExpanded: boolean;
    handleFacetToggle: (key: string) => void;
    active: KV[];
    onSelect: (item: KV) => void;
    isShowFlagLabel: boolean;
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

    const intl = useIntl();

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
      (facet.counts.length > 1 || active.some((activeItem) => activeItem.key === facet.key)) && (
        <Paper
          className={!isBooleanFacet ? classes.facetPaper : undefined}
          fs="sm"
          radius={0}
          style={{ maxHeight: 400, overflowY: 'auto' }}
        > 
        {/* Render header only for non-boolean facets */}
        {!isBooleanFacet && (
          <Group justify='space-between' style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
            <Text size='md' className={classes.facetHeader}>
              <FormattedMessage
              id={facet.key.startsWith('properties.') ? facet.key.replace('properties.', '') : facet.key}
              defaultMessage={facet.key.startsWith('properties.') ? facet.key.replace('properties.', '') : facet.key}
              />{' '}
              <InfoTooltip tooltipText={intl.formatMessage({ id: 'filters.nonBoolean.tooltip', defaultMessage: '' })} />
            </Text>
            <ActionIcon
              variant='subtle'
              color='dark'
              size='sm'
              onClick={handleToggle}
              title={`${intl.formatMessage({ id: 'filters.toggle.label', defaultMessage: 'Toggle filters for' })} ${intl.formatMessage({ id: facet.key, defaultMessage: facet.key })}`}
              aria-label={`${intl.formatMessage({ id: 'filters.toggle.label', defaultMessage: 'Toggle filters for' })} ${intl.formatMessage({ id: facet.key, defaultMessage: facet.key })}`}
            >
              <FontAwesomeIcon icon={isExpanded ? faMinus : faPlus} />
            </ActionIcon>
          </Group>
        )}
        { isBooleanFacet && isShowFlagLabel && (
            <Text size='md' className={classes.facetHeader} style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
              <FormattedMessage id='facet.flag.label' defaultMessage='List flags' />{' '}
              <InfoTooltip tooltipText={intl.formatMessage({ id: 'filters.boolean.tooltip', defaultMessage: '' })} />
            </Text>
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
    ) 
    );
  }
);

export const FiltersSection = memo(
  ({ facets, active, onSelect }: FiltersDrawerProps) => {
    const [expanded, setExpanded] = useState<string[]>([]);

    // Sort facets to ensure boolean facets are at the bottom
    const sortedFacets = useMemo(
      () =>
        facets
          .filter((facet) => facet.counts.length > 0) // Filter out empty facets
          .sort((a, b) => {
            // Sort by the first occurrence of boolean facets
            if (BOOLEAN_FACETS.includes(a.key) && !BOOLEAN_FACETS.includes(b.key)) {
              return 1; 
            }
            if (!BOOLEAN_FACETS.includes(a.key) && BOOLEAN_FACETS.includes(b.key)) {
              return -1; 
            }
            // For other facets, sort by the key
            return a.key.localeCompare(b.key);      
          }
          ),
      [facets]
    );
    
    // Store the first indices of boolean facets
    const firstBooleanIndex = sortedFacets.findIndex(
      (item) => BOOLEAN_FACETS.includes(item.key) && item.counts.length > 0
    );

    const emptyFacets = useMemo(
      () => facets.filter((facet) => facet.counts.length <= 1), // we ignore facets with a single count value, as they are not useful
      [facets]
    );
    const hasEmptyFacets = emptyFacets.length === facets.length;

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
      // Expand the first facet by default
      if (facets.length > 0) {
        setExpanded((prevExpanded) =>
          prevExpanded.length === 0
            ? facets
          .filter((facet) => facet.counts.length > 2) // Only expand facets with more than 2 counts (e.g., not boolean facets)
          .slice(0, 1) // Expand only the first one
          .map((facet) => facet.key)
            : prevExpanded
        );
      }
    }, [facets]);

    return (
      <>
        <Text size='lg' fw='bold' opacity={0.85}>
          <FormattedMessage id='filters.title' defaultMessage='Refine results' />
        </Text>
        <Stack gap={4} mt="xs" mb="md" pb={4}>
          { hasEmptyFacets && (
            <Text size='sm' color='dimmed'>
              <FormattedMessage id='filters.empty' defaultMessage='No filters available' />
            </Text>
          )}
          { sortedFacets.map((facet, index) => {
            const isFirst = index === firstBooleanIndex;
            return (
              <FacetComponent
                key={facet.key}
                facet={facet}
                isExpanded={expanded.includes(facet.key)}
                handleFacetToggle={handleFacetToggle}
                active={active}
                onSelect={onSelect}
                isShowFlagLabel={isFirst}
              />
            );
          })}
        </Stack>
      </>
    );
  }
);

export const ActiveFilters = memo((
  {
    active,
    handleFilterClick,
    resetFilters,
  }: {
    active: KV[];
    handleFilterClick: (item: KV) => void;
    resetFilters: () => void;
}) => {
  const intl = useIntl();

  return (
    <>
      <Text component='span' fs='xs' className={classes.activeFiltersText}>
        <FormattedMessage id='filters.active' defaultMessage='selected filters' />:{' '}
      </Text>
      {active.map((filter) => (
        <Paper 
          key={filter.key} 
          fs='sm' 
          radius='sm' 
          bd='1px solid var(--mantine-color-default-border)' 
          className={classes.activeFiltersPaper}
        >
          <Text component='div' fs='xs' className={classes.activeFiltersText}>
            <FormattedMessage id={filter.key} defaultMessage={filter.key.startsWith('properties.') ? filter.key.replace('properties.', '') : filter.key}/>
            { filter.value && filter.value !== 'true' && filter.value !== 'false' && (
              <>
                :{' '}
                <FormattedMessage id={filter.value} defaultMessage={filter.value}/>
              </>
            )}
          </Text>
          <ActionIcon
            radius='sm'
            opacity={0.8}
            size='xs'
            ml='xs'
            mt={1}
            title={`${intl.formatMessage({ id: 'filters.remove.label', defaultMessage: 'Remove filter for' })} ${intl.formatMessage({ id: filter.key, defaultMessage: filter.key })}`}
            aria-label={`${intl.formatMessage({ id: 'filters.remove.label', defaultMessage: 'Remove filter for' })} ${intl.formatMessage({ id: filter.key, defaultMessage: filter.key })}`}
            onClick={() => handleFilterClick(filter)}
          >
            <FontAwesomeIcon icon={faClose} fontSize={14} />
          </ActionIcon>
        </Paper>
      ))}
      <Paper 
        fs='sm'
        radius='sm'
        className={classes.activeFiltersRemoveAll}
        onClick={resetFilters}
        title={intl.formatMessage({ id: 'filters.clearAll.label', defaultMessage: 'Clear all filters' })}
        aria-label={intl.formatMessage({ id: 'filters.clearAll.label', defaultMessage: 'Clear all filters' })}
      >
        <Text component='div' fs='xs' className={classes.activeFiltersText}>
          <FormattedMessage id='filters.reset' defaultMessage='Clear all filters' />
        </Text>
        <FontAwesomeIcon icon={faDeleteLeft} fontSize={22} color='var(--mantine-primary-color-filled)' style={{ marginLeft: 8 }}/>
      </Paper>
    </>
  )
})