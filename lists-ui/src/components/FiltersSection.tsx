import {
  faAngleDown,
  faAngleUp,
  faChartDiagram,
  faDeleteLeft,
  faInfoCircle,
  faSliders
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  ActionIcon,
  Button,
  Checkbox,
  Chip,
  Collapse,
  Group,
  Paper,
  Pill,
  Stack,
  Text,
  ThemeIcon,
  Tooltip
} from '@mantine/core';
import { Fragment, memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';

import { Constraint, Facet, KV } from '#/api';
import { useConstraints } from '#/api/graphql/useConstraints';
import { useALA } from '#/helpers/context/useALA';
import sanitiseText from '#/helpers/utils/sanitiseText';
import { ListTypeBadge } from './ListTypeBadge';

import classes from './FiltersSection.module.css';

interface FiltersDrawerProps {
  facets: Facet[];
  active: KV[];
  showExpand?: boolean;
  onSelect: (item: KV) => void;
  onReset: () => void;
  loading?: boolean;
  preserveOrder?: boolean;
}

export const BOOLEAN_FACETS = ['isAuthoritative', 'isSDS', 'isBIE', 'hasRegion', 'isThreatened', 'isInvasive', 'isBiosecurity'];
const CORE_FACETS = ['listType'];

// Helper function to render the entire Checkbox with its label
function RenderCheckbox(
  facetName: string,
  key: string,
  countItem: { value: string; count: number } | undefined,
  isChecked: boolean,
  isBooleanFacet: boolean,
  onChange: () => void, // Accept the specific onChange handler
  facetConstraints?: Constraint[]
) {
  const intl = useIntl();

  // Determine the correct message ID with fallback (needed for isPrivate facet)
  const primaryKey = `facet.${facetName}.${key}`; // isPrivate values only
  const fallbackKey = key || 'filter.key.missing'; // all other facets (so its backwards compatible)
  const messages = intl.messages; // load all messages into an object
  const messageId = messages[primaryKey] ? primaryKey : fallbackKey; // check if primaryKey exists, else use fallbackKey

  // For facets that have constraints (tags, licence), resolve label and tooltip from the
  // constraints data rather than relying on i18n lookups with raw values.
  const constraintMatch = facetConstraints?.find(c => c.value === key);

  // tags:    display the human-readable label from constraints (e.g. "ALA Conservation")
  // licence: keep displaying the raw value (e.g. "CC-BY") but use the constraint label
  //          as the tooltip, replacing the old i18n licence.* lookup
  // others:  fall back to i18n as before
  const displayLabel = facetName === 'tags'
    ? (constraintMatch?.label ?? intl.formatMessage({ id: messageId, defaultMessage: key }))
    : intl.formatMessage({ id: messageId, defaultMessage: key });
  const constraintTooltip = facetName === 'licence' ? constraintMatch?.label : undefined;

  if (!countItem) return null; // Handle case where countItem might be undefined
  
  const isDisabled = !isChecked && countItem.count === 0;

  return (
    <Checkbox
      key={key} // Use the provided key
      size='xs'
      disabled={isDisabled}
      classNames={{
        root: !isBooleanFacet 
          ? `${classes.checkboxRoot} ${isDisabled ? classes.checkboxRootDisabled : ''}` 
          : (isDisabled ? classes.checkboxRootDisabled : undefined),
        body: classes.checkboxBody,
        inner: classes.checkboxInner,
        labelWrapper: classes.checkboxLabelWrapper,
        label: classes.checkboxLabel,
      }}
      onChange={onChange} // Use the provided onChange handler
      checked={isChecked}
      label={
        // The label structure remains the same
        <Paper className={`${classes.checkboxPaper} ${isDisabled ? classes.checkboxPaperDisabled : ''}`}>
          <ListTypeBadge 
            listTypeValue={key} 
            iconSide='right' 
            titleText={displayLabel}
            tooltipText={constraintTooltip}
          />
          <Chip
            size="xs"
            checked={isChecked}
            disabled={isDisabled}
            classNames={{
              root: `${classes.countsChipRoot} ${isDisabled ? classes.countsChipDisabled : ''}`,
              label: classes.countsChipLabel,
              iconWrapper: classes.countsChipIconWrapper,
            }}
            style={{ marginLeft: 'auto' }}
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
    <Tooltip label={tooltipText} withArrow position="top" component="span" >
      <ThemeIcon size="sm" variant="transparent" color="main" opacity={0.8} style={{ cursor: 'pointer' }}>
        <FontAwesomeIcon icon={faInfoCircle} size="sm" />
      </ThemeIcon>
    </Tooltip>
  );
}

// Function to remove prefixes and format the filter key
const removeFilterPrefix = (key: string) => {
  if (!key) return '';
  let cleaned = key;
  if (cleaned.startsWith('properties.')) {
    cleaned = cleaned.replace('properties.', '');
  } else if (cleaned.startsWith('classification.')) {
    cleaned = cleaned.replace('classification.', '');
  }
  if (cleaned === 'classs') {
    return 'Class';
  }
  if (cleaned === 'listType') {
    return 'Type';
  }
  if (cleaned === 'tags') {
    return 'Tags';
  }
  return cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
};

const FacetComponent = memo(
  ({
    facet,
    isExpanded,
    handleFacetToggle,
    active,
    onSelect,
    isShowFlagLabel,
    showExpand = true,
    constraintMap,
  }: {
    facet: Facet;
    isExpanded: boolean;
    handleFacetToggle: (key: string) => void;
    active: KV[];
    onSelect: (item: KV) => void;
    isShowFlagLabel: boolean;
    showExpand?: boolean;
    /** Map of facet key → Constraint[] for facets whose values need label resolution (e.g. tags, licence) */
    constraintMap?: Map<string, Constraint[]>;
  }) => {
    const isTag = facet.key === 'tags';
    // Constraints for this specific facet, if any
    const facetConstraints = constraintMap?.get(facet.key);
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
        (sortedCounts[0]?.value === 'true' || sortedCounts[0]?.value === 'false')
        && BOOLEAN_FACETS.includes(facet.key);
    
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

    const isClassification = facet.key.startsWith('classification.');
    const matchedTooltip = isClassification
      ? intl.formatMessage({
          id: 'filters.matched.taxonomy.tooltip',
          defaultMessage: 'Matched via ALA taxonomy',
        })
      : undefined;

    return (
      (facet.counts.length >= 1 || active.some((activeItem) => activeItem.key === facet.key)) && (
        <Paper
          className={!isBooleanFacet ? classes.facetPaper : undefined}
          fs="sm"
          radius={0}
        > 
        {/* Render header only for non-boolean facets */}
        {!isBooleanFacet && (
          <Group justify='space-between' className={classes.facetGroup}>
            <Text
              size='md'
              className={classes.facetHeader}
              span
            >
              {isTag ? (
                <FormattedMessage id='facet.tag.label' defaultMessage='Tags' />
              ) : (
                <FormattedMessage
                  id={`facet.${facet.key}.label`}
                  defaultMessage={removeFilterPrefix(facet.key)}
                />
              )}
              {facet.key !== 'isPrivate' && (
                <>
                  {' '}
                  <Text span className={classes.qualifier}>
                    <FormattedMessage
                      id={isTag ? 'filters.qualifier.all' : 'filters.qualifier.any'}
                      defaultMessage={isTag ? '(all)' : '(any)'}
                    />
                  </Text>
                  {' '}
                  <InfoTooltip
                    tooltipText={intl.formatMessage(
                      isTag
                        ? { id: 'filters.tags.tooltip', defaultMessage: 'Each entry can have multiple tags. Results must have all the tags you select.' }
                        : { id: 'filters.any.tooltip', defaultMessage: 'Each entry has only one value for this filter. Results can match any of the options you select.' }
                    )}
                  />
                </>
              )}
              {isClassification && (
                <Tooltip label={matchedTooltip} withArrow position="top" component="span" title={matchedTooltip}>
                  <ActionIcon
                    size="sm"
                    variant="transparent"
                    color="main"
                    opacity={0.8}
                    style={{ cursor: 'pointer', display: 'inline-flex' }}
                    aria-label={matchedTooltip}
                    title={matchedTooltip}
                  >
                    <FontAwesomeIcon icon={faChartDiagram} size="sm" />
                  </ActionIcon>
                </Tooltip>
              )}
            </Text>
            {showExpand && (
              <ActionIcon
                mt={5}
                variant='subtle'
                color='dark'
                size='sm'
                onClick={handleToggle}
                title={`${intl.formatMessage({ id: 'filters.toggle.label', defaultMessage: 'Toggle filters for' })} ${intl.formatMessage({ id: isTag ? 'facet.tag.label' : (facet.key || 'filter.key.missing'), defaultMessage: isTag ? 'Tags' : facet.key })}`}
                aria-label={`${intl.formatMessage({ id: 'filters.toggle.label', defaultMessage: 'Toggle filters for' })} ${intl.formatMessage({ id: isTag ? 'facet.tag.label' : (facet.key || 'filter.key.missing'), defaultMessage: isTag ? 'Tags' : facet.key })}`}
              >
                <FontAwesomeIcon icon={isExpanded ? faAngleUp : faAngleDown} />
              </ActionIcon>
            )}
          </Group>
        )}
        { (isBooleanFacet && isShowFlagLabel) && (
            <Text 
              size='md' 
              span 
              className={classes.facetHeader + ' ' + classes.facetHeaderBoolean} 
              title={intl.formatMessage({ id: 'filters.flags.tooltip', defaultMessage: 'Each entry can have multiple flags. Results must have all the flags you select.' })}
              >
              <FormattedMessage id='facet.flag.label' defaultMessage='Flags' />
              {' '}
              <Text span className={classes.qualifier}>
                <FormattedMessage id='filters.qualifier.all' defaultMessage='(all)' />
              </Text>
              {' '} 
              <InfoTooltip tooltipText={intl.formatMessage({ id: 'filters.flags.tooltip', defaultMessage: 'Each entry can have multiple flags. Results must have all the flags you select.' })} />
            </Text>
        )}
        {/* Render checkboxes using the helper */}
        {isBooleanFacet ? (
          // --- Boolean Facet Rendering ---
          (() => {
            const booleanItem = sortedCounts.find((c) => c.value === 'true') || sortedCounts[1];
            const isChecked = isValueActive(booleanItem?.value);
            if (!isChecked && (!booleanItem || booleanItem.count <= 0)) {
              return null;
            }
            return RenderCheckbox(
              facet.key, // Pass the facet key for proper labeling
              facet.key, // Key for the single boolean checkbox
              booleanItem,
              isChecked,
              isBooleanFacet,
              handleBooleanChange, // Pass the specific handler
              facetConstraints
            );
          })()
        ) : (
          // --- Non-boolean Facet Rendering ---
          <Collapse in={!showExpand || isExpanded} className={classes.collapse}>
            {sortedCounts.map((item) => {
              const isChecked = isValueActive(item.value);
              return RenderCheckbox(
                facet.key, // Key for the checkbox group
                item.value, // Key is the item value
                item,
                isChecked,
                isBooleanFacet,
                handleItemChange(item.value), // Pass the specific handler for this item
                facetConstraints
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
  ({ facets, active, onSelect, showExpand, loading = false, preserveOrder = false }: FiltersDrawerProps) => {
    const ala = useALA();
    const { constraints } = useConstraints(ala);

    const handleSelect = useCallback((item: KV) => {
      if (loading) return;
      onSelect(item);
    }, [loading, onSelect]);

    // Build a map of facet key → Constraint[] for facets whose values need label
    // resolution from the server-supplied constraints (e.g. tags, licence).
    const constraintMap = useMemo(() => {
      const map = new Map<string, Constraint[]>();
      if (constraints?.tags)    map.set('tags',    constraints.tags);
      if (constraints?.licence) map.set('licence', constraints.licence);
      return map;
    }, [constraints?.tags, constraints?.licence]);

    // Sort facets or preserve order if requested
    const sortedFacets = useMemo(
      () => {
        const filtered = facets.filter((facet) => {
          if (facet.counts.length === 0) return false;
          // For boolean facets, only show if active OR count of "true" > 0
          if (BOOLEAN_FACETS.includes(facet.key)) {
            const isActive = active.some((a) => a.key === facet.key);
            if (isActive) return true;
            const trueCount = facet.counts.find((c) => c.value === 'true')?.count ?? 0;
            return trueCount > 0;
          }
          return true;
        });

        if (preserveOrder) {
          return filtered;
        }

        return [...filtered].sort((a, b) => {
          // Sort BOOLEAN_FACETS to be the first items
          if (BOOLEAN_FACETS.includes(a.key) && !BOOLEAN_FACETS.includes(b.key)) {
            return -1;
          }
          if (!BOOLEAN_FACETS.includes(a.key) && BOOLEAN_FACETS.includes(b.key)) {
            return 1;
          }
          // Sort CORE_FACETS to be after BOOLEAN_FACETS
          if (CORE_FACETS.includes(a.key) && !CORE_FACETS.includes(b.key)) {
            return BOOLEAN_FACETS.includes(b.key) ? 1 : -1;
          }
          if (!CORE_FACETS.includes(a.key) && CORE_FACETS.includes(b.key)) {
            return BOOLEAN_FACETS.includes(a.key) ? -1 : 1;
          }
          // For other facets, sort by the key
          return a.key.localeCompare(b.key);
        });
      },
      [facets, active, preserveOrder]
    );

    const isExpandableFacet = useCallback((facet: Facet) => {
      if (!facet.counts || facet.counts.length === 0) return false;
      if (BOOLEAN_FACETS.includes(facet.key)) return false;
      const isBool = facet.counts.length <= 2 &&
        (facet.counts[0]?.value === 'true' || facet.counts[0]?.value === 'false');
      return !isBool;
    }, []);

    const initialExpandedSet = useRef(false);

    // Lazy initializer runs once on mount — expands the first 2 non-boolean facets
    const [expanded, setExpanded] = useState<string[]>(() => {
      const toExpand = sortedFacets
        .filter(isExpandableFacet)
        .slice(0, 2)
        .map(facet => facet.key);
      if (toExpand.length > 0) {
        initialExpandedSet.current = true;
      }
      return toExpand;
    });

    useEffect(() => {
      if (!initialExpandedSet.current && sortedFacets.length > 0) {
        const toExpand = sortedFacets
          .filter(isExpandableFacet)
          .slice(0, 2)
          .map(facet => facet.key);
        if (toExpand.length > 0) {
          setExpanded(toExpand);
          initialExpandedSet.current = true;
        }
      }
    }, [sortedFacets, isExpandableFacet]);
    
    // Store the first indices of boolean facets
    const firstBooleanIndex = sortedFacets.findIndex(
      (item) => BOOLEAN_FACETS.includes(item.key)
    );

    const emptyFacets = useMemo(
      () => sortedFacets.length === 0,
      [sortedFacets]
    );
    const hasEmptyFacets = emptyFacets;

    // Callback function for facet toggling
    const handleFacetToggle = useCallback((key: string) => {
      setExpanded((prevExpanded) =>
        prevExpanded.includes(key)
          ? prevExpanded.filter((item) => item !== key)
          : [...prevExpanded, key]
      );
    }, []);

    return (
      <div className={loading ? classes.filtersLoading : undefined}>
        <Text size='md' fw='bold' opacity={0.85} pb={2}>
          <FormattedMessage id='filters.title' defaultMessage='Refine results' />
        </Text>
        <Stack gap={2} mt={3} mb="md" pb={4}>
          { hasEmptyFacets && (
            <Text size='sm' color='dimmed'>
              <FormattedMessage id='filters.empty' defaultMessage='No filters available' />
            </Text>
          )}
          { sortedFacets.map((facet, index) => {
            const isFirst = index === firstBooleanIndex;
            return (
              <FacetComponent
                key={`facet-${facet.key}-${index}`} // include index to ensure uniqueness even if keys are duplicated
                facet={facet}
                isExpanded={expanded.includes(facet.key)}
                handleFacetToggle={handleFacetToggle}
                active={active}
                onSelect={handleSelect}
                isShowFlagLabel={isFirst}
                showExpand={showExpand}
                constraintMap={constraintMap}
              />
            );
          })}
        </Stack>
      </div>
    );
  }
);

/**
 * ActiveFilters component that displays the currently selected filters
 * and allows users to remove them, or reset all filters.
 * 
 * @param {Object} props - The component props
 * @param {KV[]} props.active - The currently active filters.
 * @param {Function} props.handleFilterClick - Function to handle filter removal.
 * @param {Function} props.resetFilters - Function to reset all filters.
 * @returns {JSX.Element} The rendered component.
 */
interface ActiveFilterItem {
  filter: KV;
  displayValue: string;
  title?: string;
  removeLabel: string;
}

interface ActiveFilterGroup {
  key: string;
  label: string;
  connector: 'and' | 'or';
  items: ActiveFilterItem[];
}

export const ActiveFilters = memo((
  {
    active,
    handleFilterClick,
    resetFilters,
    loading = false,
  }: {
    active: KV[];
    handleFilterClick: (item: KV) => void;
    resetFilters: () => void;
    loading?: boolean;
}) => {
  const intl = useIntl();
  const ala = useALA();
  const { constraints } = useConstraints(ala);

  const filterGroups = useMemo<ActiveFilterGroup[]>(() => {
    if (!active || active.length === 0) return [];

    const groups: ActiveFilterGroup[] = [];
    const groupMap = new Map<string, ActiveFilterGroup>();

    active.forEach((filter) => {
      const isBoolean = BOOLEAN_FACETS.includes(filter.key);
      let groupKey: string;
      let groupLabel: string;
      let connector: 'and' | 'or';

      if (isBoolean) {
        groupKey = 'flags';
        groupLabel = intl.formatMessage({ id: 'filters.group.flags', defaultMessage: 'Flags' });
        connector = 'and';
      } else if (filter.key === 'tags') {
        groupKey = 'tags';
        groupLabel = intl.formatMessage({ id: 'facet.tag.label', defaultMessage: 'Tags' });
        connector = 'and';
      } else if (filter.key === 'listType') {
        groupKey = 'listType';
        groupLabel = intl.formatMessage({ id: 'filters.group.type', defaultMessage: 'Type' });
        connector = 'or';
      } else {
        groupKey = filter.key;
        const keyMessageId = `facet.${filter.key}.label`;
        const fallbackMessageId = sanitiseText(filter.key);
        groupLabel = intl.messages[keyMessageId]
          ? intl.formatMessage({ id: keyMessageId })
          : intl.messages[fallbackMessageId]
            ? intl.formatMessage({ id: fallbackMessageId })
            : removeFilterPrefix(filter.key);
        connector = 'or';
      }

      if (!groupMap.has(groupKey)) {
        const newGroup: ActiveFilterGroup = {
          key: groupKey,
          label: groupLabel,
          connector,
          items: [],
        };
        groupMap.set(groupKey, newGroup);
        groups.push(newGroup);
      }

      let displayValue = filter.value;
      if (isBoolean) {
        const flagMessageId = sanitiseText(filter.key);
        displayValue = intl.messages[flagMessageId]
          ? intl.formatMessage({ id: flagMessageId })
          : removeFilterPrefix(filter.key);
      } else if (filter.key === 'tags') {
        displayValue = constraints?.tags?.find((c) => c.value === filter.value)?.label ?? sanitiseText(filter.value);
      } else if (filter.key === 'licence') {
        displayValue = filter.value;
      } else {
        const valMessageId = sanitiseText(filter.value);
        if (intl.messages[valMessageId]) {
          displayValue = intl.formatMessage({ id: valMessageId });
        }
      }

      const isClassificationFilter = filter.key.startsWith('classification.');
      const title = isClassificationFilter
        ? intl.formatMessage({
            id: 'filters.matched.taxonomy.tooltip',
            defaultMessage: 'Matched via ALA taxonomy',
          })
        : undefined;

      const removeLabel = `${intl.formatMessage({ id: 'filters.remove.label', defaultMessage: 'Remove filter for' })} ${groupLabel}: ${displayValue}`;

      groupMap.get(groupKey)!.items.push({
        filter,
        displayValue,
        title,
        removeLabel,
      });
    });

    return groups;
  }, [active, constraints?.tags, intl]);

  if (filterGroups.length === 0) {
    return null;
  }

  return (
    <Group
      gap="md"
      wrap="wrap"
      align="center"
      className={`${classes.activeFiltersGroup} ${loading ? classes.filtersLoading : ''}`}
    >
      <Text component="span" size="xs" fw={500} className={classes.activeFiltersLabel}>
        <FormattedMessage id="filters.active" defaultMessage="Selected filters" />:
      </Text>
      {filterGroups.map((group) => (
        <Group key={group.key} gap={6} align="center" wrap="wrap" className={classes.activeFilterCluster}>
          <Text component="span" size="xs" fw={600} className={classes.activeFilterGroupLabel}>
            {group.label}:
          </Text>
          <Pill.Group>
            {group.items.map((item, itemIndex) => (
              <Fragment key={`${item.filter.key}-${item.filter.value}`}>
                {itemIndex > 0 && (
                  <Text component="span" size="xs" c="dimmed" fs="italic" className={classes.connectorText}>
                    <FormattedMessage id={`filters.connector.${group.connector}`} defaultMessage={group.connector} />
                  </Text>
                )}
                <Pill
                  size="sm"
                  withRemoveButton
                  onRemove={() => !loading && handleFilterClick(item.filter)}
                  disabled={loading}
                  title={item.title}
                  classNames={{
                    root: classes.activeFilterPill,
                    label: classes.activeFilterPillLabel,
                  }}
                  removeButtonProps={{
                    'aria-label': item.removeLabel,
                    title: item.removeLabel,
                  }}
                >
                  {item.displayValue}
                </Pill>
              </Fragment>
            ))}
          </Pill.Group>
        </Group>
      ))}
      <Button
        variant="subtle"
        color="charcoal"
        size="xs"
        radius="sm"
        disabled={loading}
        onClick={() => !loading && resetFilters()}
        leftSection={<FontAwesomeIcon icon={faDeleteLeft} />}
        className={classes.clearAllButton}
        title={intl.formatMessage({ id: 'filters.clearAll.label', defaultMessage: 'Clear all filters' })}
        aria-label={intl.formatMessage({ id: 'filters.clearAll.label', defaultMessage: 'Clear all filters' })}
      >
        <FormattedMessage id="filters.reset" defaultMessage="Clear all filters" />
      </Button>
    </Group>
  );
});

/**
 * ToggleFiltersButton component that displays a button to toggle the visibility of filters.
 * 
 * @param {Function} props.toggleFilters - Callback to handle filter toggle action.
 * @param {boolean} props.hidefilters - Boolean indicating whether filters are hidden or not.
 * @returns {JSX.Element} The rendered component.
 */
export function ToggleFiltersButton({ toggleFilters, hidefilters, isMobile }
    : { toggleFilters: () => void; hidefilters: boolean; isMobile?: boolean }) {
  return (
    <Button
      size= 'sm' 
      leftSection={<FontAwesomeIcon icon={faSliders} fontSize={14}/>}
      variant='default'
      classNames={{root: classes.filtersDisplayButton}}
      radius="md"
      fw="normal"
      ml={isMobile ? undefined : 'auto'}
      onClick={toggleFilters}
    >
      { hidefilters 
        ? <FormattedMessage id='filters.show' defaultMessage='Show Filters' />  
        : <FormattedMessage id='filters.hide' defaultMessage='Hide Filters' />
    }
    </Button>
  );
}