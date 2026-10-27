import { faEdit, faTrashAlt } from '@fortawesome/free-regular-svg-icons';
import {
  faBars,
  faDownload,
  faGlobe,
  faPlus,
  faRefresh,
  faSearch,
  faTableColumns,
  faUpload
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  ActionIcon,
  Button,
  Divider,
  Flex,
  Group,
  Menu,
  Paper,
  Radio,
  Stack,
  Switch,
  Text,
  Tooltip
} from '@mantine/core';
import { useCallback, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

// Mantine Notifications & Modals manager
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';

// API & Helpers
import { KV, performGQLQuery, SpeciesList, SpeciesListSubmit } from '#/api';
import { MUTATION_LIST_UPDATE } from '#/api/queries';
import { ListMeta } from '#/components/ListMeta';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

// Local styles
import { FormattedMessage, useIntl } from 'react-intl';
import classes from './Actions.module.css';

// Radio choice shown in the action modals when list filters are active:
// search all species of the list, or only the filtered ones currently shown
function ScopeChoice({ onChange }: { onChange: (scope: 'all' | 'filtered') => void }) {
  const intl = useIntl();

  return (
    <Radio.Group
      label={intl.formatMessage({ id: 'actions.scope.label', defaultMessage: 'Species to include' })}
      defaultValue='filtered'
      onChange={(value) => onChange(value as 'all' | 'filtered')}
    >
      <Stack gap='xs' mt='xs'>
        <Radio
          value='filtered'
          label={intl.formatMessage({
            id: 'actions.scope.filtered',
            defaultMessage: 'Only the filtered species currently shown'
          })}
        />
        <Radio
          value='all'
          label={intl.formatMessage({ id: 'actions.scope.all', defaultMessage: 'All species in this list' })}
        />
      </Stack>
    </Radio.Group>
  );
}

interface ActionsProps {
  meta: SpeciesList;
  filters: KV[];
  editing: boolean;
  rematching: boolean;
  onEditingChange: (editing: boolean) => void;
  onMetaEdited: (meta: SpeciesListSubmit) => void;
  onRematch: () => void;
  handleAddClick: () => void;
}

export function Actions({
  meta,
  filters,
  editing,
  rematching,
  onEditingChange,
  onMetaEdited,
  onRematch,
  handleAddClick,
}: ActionsProps) {
  const [updating, setUpdating] = useState<boolean>(false);
  const [deleting, setDeleting] = useState<boolean>(false);
  const [fetchingQid, setFetchingQid] = useState<string | null>(null);
  const listQuery = useRef<{ query?: string; qid?: string } | null>(null);
  // Species scope ('all' | 'filtered') chosen in the modals, kept in a ref to
  // avoid stale closures in the modal onConfirm callbacks
  const scopeRef = useRef<'all' | 'filtered'>('filtered');
  const navigate = useNavigate();
  const intl = useIntl();
  const isReingest = location.pathname.endsWith('reingest');

  const ala = useALA();
  const authorisedForList = ala.isAuthorisedForList(meta);
  const hasFilters = filters.length > 0;
  // Maximum entries to use in a Biocache records search
  // Value may change, see lists-service/src/main/java/au/org/ala/listsapi/service/BiocacheService.java#getQidForSpeciesList
  const maxTaxaSearch = 2000; 

  // Authoritative Biocache link handler (moved before handleBiocacheLink)
  // Unfiltered authoritative lists link with the species list id as an fq -
  // biocache expands species_list:<id> server-side, so no QID is needed
  const handleAuthoritativeBiocacheLink = useCallback((dataResourceId: string) => {
    const url = import.meta.env.VITE_ALA_BIOCACHE_OCC_SEARCH;
    window.open(`${url}?q=*:*&fq=species_list:${dataResourceId}`, '_blank');
  }, []);

  // Public list Biocache link handler
  const handlePublicBiocacheLink = useCallback((dataResourceId: string) => {
    const url = import.meta.env.VITE_ALA_BIOCACHE_OCC_SEARCH;
    window.open(`${url}?q=species_list:${dataResourceId}`, '_blank');
  }, []);

  // Open a hub URL with the given service result: raw query when available,
  // otherwise the QID plus a warning about its limited shelf life
  const openWithQuery = useCallback(
    (url: string, result: { query?: string; qid?: string }) => {
      if (result.query) {
        window.open(`${url}?q=${encodeURIComponent(result.query)}`, '_blank');
      } else if (result.qid) {
        window.open(`${url}?q=qid:${result.qid}`, '_blank');
        notifications.show({
          message: intl.formatMessage({
            id: 'actions.qidExpiryWarning',
            defaultMessage:
              'Because the query for this list is very long, it cannot be included in the link directly. Instead a temporary query ID (QID) was created and the link refers to it. Links using this QID stop working after one week - please revisit this list to get a fresh link.'
          }),
          color: 'yellow',
          position: 'bottom-left',
          radius: 'md',
        });
      }
    },
    [intl]
  );

  // Redirect to a hub URL using the query built by the lists-service for this
  // list. The service returns a raw query string when it is small enough to
  // live in a URL (shareable, never expires); only oversized queries come back
  // as a QID, which expires after one week - the user is warned about that.
  const handleQueryRedirect = useCallback(
    async (url: string, scopedFilters?: KV[]) => {
      const scoped = Boolean(scopedFilters?.length);

      if (!scoped && listQuery.current) {
        openWithQuery(url, listQuery.current);
        return;
      }

      setFetchingQid(url);
      try {
        const result = await ala.rest.lists.qid(meta.id, scoped ? scopedFilters : undefined);
        if (!scoped) listQuery.current = result;

        openWithQuery(url, result);
      } catch (error) {
        notifications.show({
          message: getErrorMessage(error),
          position: 'bottom-left',
          radius: 'md',
        });

        return;
      } finally {
        setFetchingQid(null);
      }
    },
    [ala, meta, intl, openWithQuery]
  );

  // Handle Biocache links (now can reference the above functions)
  const handleBiocacheLink = useCallback(
    (dataResourceId: string, scopedFilters?: KV[]) => {
      // A filtered subset of the list can only be expressed through a query
      // built from the filtered item set. Public lists are resolved by
      // biocache itself via species_list:<id>; private lists and scoped
      // filters go through the lists-service query/QID flow.
      if (scopedFilters && scopedFilters.length > 0) {
        handleQueryRedirect(import.meta.env.VITE_ALA_BIOCACHE_OCC_SEARCH, scopedFilters);
      } else if (meta.isAuthoritative && !meta.isPrivate) {
        handleAuthoritativeBiocacheLink(dataResourceId);
      } else if (!meta.isPrivate) {
        handlePublicBiocacheLink(dataResourceId);
      } else {
        handleQueryRedirect(import.meta.env.VITE_ALA_BIOCACHE_OCC_SEARCH, undefined);
      }
    },
    [meta.isAuthoritative, meta.isPrivate, handleAuthoritativeBiocacheLink, handlePublicBiocacheLink, handleQueryRedirect]
  );

  // Occurrence records modal: the existing confirm modal, extended with a
  // species-scope choice when filters are active
  const handleOccurrenceRecordsClick = useCallback(() => {
    const description = ((meta.isAuthoritative && !meta.isPrivate) || !meta.isPrivate) ? (
      <Text>
        {intl.formatMessage({
          id: 'actions.occurrenceRecords.authoritative',
          defaultMessage: 'All taxa from this list will be used in the following Biocache search.'
        })}{' '}
        {!meta.isAuthoritative && intl.formatMessage({
          id: 'actions.occurrenceRecords.delayMsg',
          defaultMessage: 'Note: There may be a delay in the occurrence record page loading with larger lists.'
        })}
      </Text>
    ) : (
      <Text>
        {intl.formatMessage({
        id: 'actions.occurrenceRecords.limited',
        defaultMessage: 'Only the first {limit} taxa from this list will be used in the following Biocache search, due to querying limitations.'
        }, { limit: maxTaxaSearch })}
      </Text>
    );

    scopeRef.current = 'filtered';

    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          {intl.formatMessage({
            id: 'actions.occurrenceRecords.title',
            defaultMessage: 'View Occurrence Records'
          })}
        </Text>
      ),

      children: (
        <Stack gap='sm'>
          {description}
          {hasFilters && <ScopeChoice onChange={(scope) => (scopeRef.current = scope)} />}
        </Stack>
      ),
      labels: {
        confirm: hasFilters
          ? intl.formatMessage({ id: 'actions.occurrenceRecords.viewRecords', defaultMessage: 'View records' })
          : intl.formatMessage({ id: 'actions.occurrenceRecords.proceed', defaultMessage: 'View all records' }),
        cancel: intl.formatMessage({ id: 'actions.cancel', defaultMessage: 'Cancel' })
      },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: () => {
        handleBiocacheLink(
          meta.dataResourceUid,
          hasFilters && scopeRef.current === 'filtered' ? filters : undefined
        );
      },
      centered: true
    });
  }, [intl, meta, hasFilters, filters, handleBiocacheLink]);

  // Spatial portal: the spatial-hub accepts any biocache q term as a plain URL
  // parameter (urlParamsService.js) and registers its own QID server-side, so
  // public lists can be linked directly with the species_list filter. Only
  // private lists and scoped (filtered) subsets need the lists-service flow.
  const handleSpatialPortalLink = useCallback(
    (dataResourceId: string, scopedFilters?: KV[]) => {
      if (scopedFilters && scopedFilters.length > 0) {
        handleQueryRedirect(import.meta.env.VITE_ALA_SPATIAL, scopedFilters);
      } else if (!meta.isPrivate) {
        window.open(`${import.meta.env.VITE_ALA_SPATIAL}?q=species_list:${dataResourceId}`, '_blank');
      } else {
        handleQueryRedirect(import.meta.env.VITE_ALA_SPATIAL, undefined);
      }
    },
    [meta.isPrivate, handleQueryRedirect]
  );

  // Spatial portal: only asks about the scope when filters are active,
  // otherwise redirects straight away as before
  const handleSpatialPortalClick = useCallback(() => {
    if (!hasFilters) {
      handleSpatialPortalLink(meta.dataResourceUid);
      return;
    }

    scopeRef.current = 'filtered';

    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          {intl.formatMessage({ id: 'actions.spatialPortal.title', defaultMessage: 'View in Spatial Portal' })}
        </Text>
      ),
      children: (
        <Stack gap='sm'>
          <Text>
            {intl.formatMessage({
              id: 'actions.spatialPortal.filtersActive',
              defaultMessage: 'Filters are active on this list. Choose which species to include in the search.'
            })}
          </Text>
          <ScopeChoice onChange={(scope) => (scopeRef.current = scope)} />
        </Stack>
      ),
      labels: {
        confirm: intl.formatMessage({
          id: 'actions.spatialPortal.proceed',
          defaultMessage: 'View in spatial portal'
        }),
        cancel: intl.formatMessage({ id: 'actions.cancel', defaultMessage: 'Cancel' })
      },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: () => {
        handleSpatialPortalLink(
          meta.dataResourceUid,
          scopeRef.current === 'filtered' ? filters : undefined
        );
      },
      centered: true
    });
  }, [intl, hasFilters, filters, handleSpatialPortalLink]);

  // Download callback handler
  const handleDownload = useCallback(async () => {
    try {
      await ala.rest.lists.download(meta.id);
    } catch (error) {
      notifications.show({
        message: getErrorMessage(error),
        position: 'bottom-left',
        radius: 'md',
      });
    }
  }, [ala, meta]);

  // Reingest callback handler
  const handleReingest = useCallback(() => {
    navigate(`/list/${meta.id}/reingest`);
  }, [navigate, meta]);

  // Delete callback handler
  const handleDelete = useCallback(() => {
    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          Confirm list deletion
        </Text>
      ),
      children: (
        <Text>
          Please confirm that you wish to delete <b>{meta.title}</b>, this
          action cannot be undone
        </Text>
      ),
      labels: { confirm: 'Confirm', cancel: 'Cancel' },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: async () => {
        setDeleting(true);
        try {
          // Fire off the delete request
          await ala.rest.lists.delete(meta.id);
          navigate('/');
        } catch (error) {
          notifications.show({
            message: getErrorMessage(error),
            position: 'bottom-left',
            radius: 'md',
          });

          setDeleting(true);
        }
      },
    });
  }, [ala, meta, navigate]);

  const handleRematch = useCallback(() => {
    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          Confirm list rematch
        </Text>
      ),
      children: (
        <Text>
          Please confirm that you wish to rematch <b>{meta.title}</b>
        </Text>
      ),
      labels: { confirm: 'Confirm', cancel: 'Cancel' },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: async () => {
        try {
          // Fire off the rematch request
          onRematch();
          await ala.rest.lists.rematch(meta.id);
        } catch (error) {
          // notifications.show({
          //   message: getErrorMessage(error),
          //   position: 'bottom-left',
          //   radius: 'md',
          // });
        }
      },
    });
  }, [ala, meta, onRematch]);

  const handleMetaEdit = useCallback(() => {
    modals.open({
      title: (
        <Text fw='bold' size='lg'>
          {intl.formatMessage({id:'actions.editMetadata.title', defaultMessage:'Edit List Metadata'})}
        </Text>
      ),
      size: 'xl',
      children: (
        <ListMeta
          ala={ala}
          initialValues={meta}
          onReset={() => modals.closeAll()}
          onSubmit={async (values) => {
            modals.closeAll();
            setUpdating(true);

            try {
              // Update the list
              await performGQLQuery(
                MUTATION_LIST_UPDATE,
                {
                  id: meta.id,
                  ...values,
                },
                ala.token
              );

              // Show success notification
              notifications.show({
                message: (
                    <>
                    <b>{values.title}</b>{' '}
                    {intl.formatMessage({
                      id: 'actions.editMetadata.success',
                      defaultMessage: 'updated successfully',
                    })}
                    </>
                ),
                position: 'bottom-left',
                radius: 'md',
              });

              onMetaEdited(values);
            } catch (error) {
              // Show the error notificaiton
              notifications.show({
                message: getErrorMessage(error as Error),
                position: 'bottom-left',
                radius: 'md',
              });
            }

            setUpdating(false);
          }}
        />
      ),
    });
  }, [ala, meta]);

  return (
    <>
      <Menu shadow='md' width={200} position='bottom-end' radius='lg'>
        <Menu.Target>
          <ActionIcon
            className={classes.mobile}
            variant='light'
            size='md'
            radius='lg'
            aria-label={intl.formatMessage({ id: 'actions.menu.ariaLabel', defaultMessage: 'List actions' })}
          >
            {/* <DotsThreeIcon /> */}
            <FontAwesomeIcon icon={faBars} />
          </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item
            onClick={handleDownload}
            disabled={updating || rematching || deleting}
            leftSection={<FontAwesomeIcon icon={faDownload} />}
            >
            {intl.formatMessage({ id: 'actions.downloadList', defaultMessage: 'Download list' })}
          </Menu.Item>
          <Menu.Item
            onClick={handleOccurrenceRecordsClick}
            disabled={Boolean(fetchingQid)}
            leftSection={<FontAwesomeIcon icon={faSearch} />}
          >
            Occurrence records
          </Menu.Item>
          <Menu.Item
            onClick={handleSpatialPortalClick}
            disabled={Boolean(fetchingQid)}
            leftSection={<FontAwesomeIcon icon={faGlobe} />}
          >
            {intl.formatMessage({ id: 'actions.spatialPortal', defaultMessage: 'Spatial portal' })}
          </Menu.Item>
          {authorisedForList && (
            <>
              <Menu.Divider />
              <Menu.Item
                onClick={handleMetaEdit}
                disabled={updating || rematching || deleting}
                leftSection={<FontAwesomeIcon icon={faEdit} />}
              >
                {intl.formatMessage({ id: 'actions.editMetadata', defaultMessage: 'Edit metadata' })}
              </Menu.Item>
              <Menu.Item
                onClick={handleRematch}
                disabled={updating || rematching || deleting}
                leftSection={<FontAwesomeIcon icon={faRefresh} />}
              >
                {intl.formatMessage({ id: 'actions.rematchList', defaultMessage: 'Rematch list' })}
              </Menu.Item>
              <Menu.Item
                onClick={handleReingest}
                disabled={updating || rematching || deleting}
                leftSection={<FontAwesomeIcon icon={faUpload} />}
              >
                {intl.formatMessage({ id: 'actions.reingestList', defaultMessage: 'Reingest list' })}
              </Menu.Item>
              <Menu.Item
                onClick={handleDelete}
                disabled={updating || rematching || deleting}
                color='red'
                leftSection={<FontAwesomeIcon icon={faTrashAlt} />}
              >
                {intl.formatMessage({ id: 'actions.deleteList', defaultMessage: 'Delete list' })}
              </Menu.Item>
              <Menu.Divider />
              <Flex
                direction='row'
                my='xs'
                mx='sm'
                align='center'
                justify='space-between'
              >
                <Flex align='center'>
                  <FontAwesomeIcon
                    icon={faTableColumns}
                    fontSize={14}
                    style={{ marginRight: 10 }}
                  />
                    <Text style={{ fontSize: 14, fontSizeAdjust: 'none' }}>
                      {intl.formatMessage({ id: 'actions.editFields', defaultMessage: 'Edit fields' })}
                    </Text>
                </Flex>
                <Switch
                  size='sm'
                  checked={editing}
                  onChange={(ev) => onEditingChange(ev.currentTarget.checked)}
                  disabled={updating || rematching || deleting}
                />
              </Flex>
              {!isReingest && (
                <Menu.Item
                  onClick={handleAddClick}
                  disabled={updating || rematching || deleting}
                  leftSection={<FontAwesomeIcon icon={faPlus} />}
                >
                  <FormattedMessage id='add.taxa.label' defaultMessage='Add species' />
                </Menu.Item>
              )}
            </>
          )}
        </Menu.Dropdown>
      </Menu>
      <Group className={classes.desktop}>
        <Group gap='xs' justify="flex-end">
          {authorisedForList && (
            <Paper 
              py={8}
              px='sm'
              radius='lg'
              withBorder
            >
              <Group gap='xs' justify="space-between">
                <Tooltip label={intl.formatMessage({ id: 'actions.editMetadata', defaultMessage: 'Edit metadata' })} withArrow position='bottom'>
                  <ActionIcon
                    onClick={handleMetaEdit}
                    disabled={rematching || deleting}
                    loading={updating}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label={intl.formatMessage({ id: 'actions.editMetadata', defaultMessage: 'Edit metadata' })}
                  >
                    <FontAwesomeIcon size='sm' icon={faEdit} />
                  </ActionIcon>
                </Tooltip>
                <Tooltip label={intl.formatMessage({ id: 'actions.rematchList', defaultMessage: 'Rematch list' })} withArrow position='bottom'>
                  <ActionIcon
                    onClick={handleRematch}
                    disabled={updating || deleting}
                    loading={rematching}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label={intl.formatMessage({ id: 'actions.rematchList', defaultMessage: 'Rematch list' })}
                  >
                    <FontAwesomeIcon size='sm' icon={faRefresh} />
                  </ActionIcon>
                </Tooltip>
                <Tooltip label={intl.formatMessage({ id: 'actions.reingestList', defaultMessage: 'Reingest list' })} withArrow position='bottom'>
                  <ActionIcon
                    onClick={handleReingest}
                    disabled={updating || deleting || rematching}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label={intl.formatMessage({ id: 'actions.reingestList', defaultMessage: 'Reingest list' })}
                  >
                    <FontAwesomeIcon size='sm' icon={faUpload} />
                  </ActionIcon>
                </Tooltip>
                <Tooltip label={intl.formatMessage({ id: 'actions.deleteList', defaultMessage: 'Delete list' })} withArrow position='bottom'>
                  <ActionIcon
                    onClick={handleDelete}
                    disabled={updating || rematching}
                    loading={deleting}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label={intl.formatMessage({ id: 'actions.deleteList', defaultMessage: 'Delete list' })}
                  >
                    <FontAwesomeIcon size='sm' icon={faTrashAlt} />
                  </ActionIcon>
                </Tooltip>
              </Group>
              <Stack gap='sm' mt='md'>
                <Tooltip  
                  label={intl.formatMessage({ id: 'actions.edit.field.tooltip', defaultMessage: 'Edit custom field column headers' })} 
                  refProp="rootRef"
                  withArrow 
                  position='bottom'
                >
                  <Switch
                    disabled={updating || rematching || deleting}
                    mr='xs'
                    size='xs'
                    label={intl.formatMessage({ id: 'actions.editFields', defaultMessage: 'Edit column headings' })}
                    checked={editing}
                    onChange={(ev) => onEditingChange(ev.currentTarget.checked)}
                  />
                </Tooltip>
                {!isReingest && (
                  <Tooltip label={intl.formatMessage({ id: 'add.species.title', defaultMessage: 'Add a new taxon entry' })} withArrow position='bottom'>
                    <Button
                      radius='lg'
                      size='xs'
                      leftSection={<FontAwesomeIcon icon={faPlus} />}
                      variant='light'
                      onClick={handleAddClick}
                      aria-label={intl.formatMessage({ id: 'add.species.title', defaultMessage: 'Add a new taxon entry' })}
                    >
                    <FormattedMessage id='add.taxa.label' defaultMessage='Add species' />
                  </Button>
                  </Tooltip>
                )}
                </Stack>
            </Paper>
          )}
          <Paper withBorder radius='lg' my={0} px={0}>
            <Button
              onClick={handleDownload}
              fullWidth
              size='sm'
              variant='subtle'
              leftSection={<FontAwesomeIcon icon={faDownload} />}
              style={{
                fontSize: '0.8rem',
                borderRadius: 0,
                borderTopLeftRadius: 14,
                borderTopRightRadius: 14,
                margin: '1px 0px',
              }}
            >
              Download list
            </Button>
            <Divider />
            <Button
              onClick={handleOccurrenceRecordsClick}
              loading={
                fetchingQid === import.meta.env.VITE_ALA_BIOCACHE_OCC_SEARCH
              }
              disabled={Boolean(fetchingQid)}
              fullWidth
              size='sm'
              variant='subtle'
              leftSection={<FontAwesomeIcon icon={faSearch} />}
              style={{
                fontSize: '0.8rem',
                borderRadius: 0,
                borderTopLeftRadius: 0,
                borderTopRightRadius: 0,
                margin: '1px 0px',
              }}
            >
              View occurrence records
            </Button>
            <Divider />
            <Button
              onClick={handleSpatialPortalClick}
              loading={fetchingQid === import.meta.env.VITE_ALA_SPATIAL}
              disabled={Boolean(fetchingQid)}
              fullWidth
              variant='subtle'
              leftSection={<FontAwesomeIcon icon={faGlobe} />}
              style={{
                fontSize: '0.8rem',
                borderRadius: 0,
                borderBottomLeftRadius: 14,
                borderBottomRightRadius: 14,
                margin: '1px 0px',
              }}
            >
              View in spatial portal
            </Button>
          </Paper>
        </Group>
      </Group>
    </>
  );
}
