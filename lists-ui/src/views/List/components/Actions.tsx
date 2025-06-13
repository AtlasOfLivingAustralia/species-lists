/* eslint-disable react-hooks/exhaustive-deps */
import { DotsThreeIcon, FolderIcon } from '@atlasoflivingaustralia/ala-mantine';
import { faEdit, faTrashAlt } from '@fortawesome/free-regular-svg-icons';
import {
  faDownload,
  faFingerprint,
  faGlobe,
  faRefresh,
  faSearch,
  faTableColumns,
  faUpload,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  ActionIcon,
  Box,
  Button,
  Divider,
  Flex,
  Group,
  Menu,
  Paper,
  Stack,
  Switch,
  Text,
  Tooltip,
} from '@mantine/core';
import { useCallback, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

// Mantine Notifications & Modals manager
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';

// API & Helpers
import { performGQLQuery, SpeciesList, SpeciesListSubmit } from '#/api';
import { MUTATION_LIST_UPDATE } from '#/api/queries';
import { ListMeta } from '#/components/ListMeta';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

// Local styles
import { useIntl } from 'react-intl';
import classes from './Actions.module.css';

interface ActionsProps {
  meta: SpeciesList;
  editing: boolean;
  rematching: boolean;
  onEditingChange: (editing: boolean) => void;
  onMetaEdited: (meta: SpeciesListSubmit) => void;
  onRematch: () => void;
}

export function Actions({
  meta,
  editing,
  rematching,
  onEditingChange,
  onMetaEdited,
  onRematch,
}: ActionsProps) {
  const [updating, setUpdating] = useState<boolean>(false);
  const [deleting, setDeleting] = useState<boolean>(false);
  const [fetchingQid, setFetchingQid] = useState<string | null>(null);
  const listQid = useRef<string | null>(null);
  const navigate = useNavigate();
  const intl = useIntl();

  const ala = useALA();
  const authorisedForList = ala.isAuthorisedForList(meta);

  // Download callback handler
  const handleQidRedirect = useCallback(
    async (url: string) => {
      if (!listQid.current) {
        setFetchingQid(url);
        try {
          listQid.current = await ala.rest.lists.qid(meta.id);
        } catch (error) {
          notifications.show({
            message: getErrorMessage(error),
            position: 'bottom-left',
            radius: 'md',
          });

          return;
        }
        setFetchingQid(null);
      }

      if (listQid.current)
        window.open(`${url}?q=qid:${listQid.current}`, '_blank');
    },
    [ala, meta, listQid.current]
  );

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

  // Download callback handler
  const handleReingest = useCallback(() => {
    navigate(`/list/${meta.id}/reingest`);
  }, [ala, meta]);

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
  }, [ala, meta]);

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
  }, [meta]);

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
            <DotsThreeIcon />
            </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
            <Menu.Label>
              {intl.formatMessage({ id: 'actions.menu.label', defaultMessage: 'Actions' })}
            </Menu.Label>
            <Menu.Item
            onClick={handleDownload}
            disabled={updating || rematching || deleting}
            leftSection={<FontAwesomeIcon icon={faDownload} />}
            >
            {intl.formatMessage({ id: 'actions.downloadList', defaultMessage: 'Download list' })}
            </Menu.Item>
          <Menu.Item
            onClick={() =>
              handleQidRedirect(import.meta.env.VITE_ALA_BIOCACHE_OCC_SEARCH)
            }
            disabled={Boolean(fetchingQid)}
            leftSection={<FontAwesomeIcon icon={faSearch} />}
          >
            Occurrence records
          </Menu.Item>
            <Menu.Item
              onClick={() => handleQidRedirect(import.meta.env.VITE_ALA_SPATIAL)}
              disabled={Boolean(fetchingQid)}
              leftSection={<FontAwesomeIcon icon={faGlobe} />}
            >
              {intl.formatMessage({ id: 'actions.spatialPortal', defaultMessage: 'Spatial portal' })}
            </Menu.Item>
          {authorisedForList && (
            <>
                <Menu.Label>
                  {intl.formatMessage({ id: 'actions.menu.administration', defaultMessage: 'Administration' })}
                </Menu.Label>
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
                  color='red'
                  leftSection={<FontAwesomeIcon icon={faRefresh} />}
                >
                  {intl.formatMessage({ id: 'actions.rematchList', defaultMessage: 'Rematch list' })}
                </Menu.Item>
                <Menu.Item
                  onClick={handleReingest}
                  disabled={updating || rematching || deleting}
                  color='red'
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
            </>
          )}
        </Menu.Dropdown>
      </Menu>
      <Box className={classes.desktop}>
        <Stack gap='xs'>
          <Paper
            miw={authorisedForList ? 285 : undefined}
            py={8}
            px='xs'
            shadow='sm'
            radius='lg'
            withBorder
          >
            <Flex>
              <Text
                fw='bold'
                style={{
                  textAlign: 'center',
                  fontSize: '0.8rem',
                  flexBasis: '100%',
                }}
              >
                <FolderIcon size={14} style={{ marginRight: 10 }} />
                {new Intl.NumberFormat().format(meta.rowCount)} total
              </Text>
              {meta.distinctMatchCount && (
                <>
                  <Divider orientation='vertical' mx='xs' />
                    <Text
                    fw='bold'
                    style={{
                      textAlign: 'center',
                      fontSize: '0.8rem',
                      flexBasis: '100%',
                    }}
                    >
                    <FontAwesomeIcon
                      icon={faFingerprint}
                      style={{ marginRight: 10 }}
                    />
                    {new Intl.NumberFormat().format(meta.distinctMatchCount)}{' '}
                    {intl.formatMessage({
                      id: 'actions.distinct',
                      defaultMessage: 'distinct',
                    })}
                    </Text>
                </>
              )}
            </Flex>
          </Paper>
          {authorisedForList && (
            <Paper
              miw={authorisedForList ? 285 : undefined}
              py={8}
              px='sm'
              shadow='sm'
              radius='lg'
              withBorder
            >
                <Group gap='xs'>
                <Switch
                  disabled={updating || rematching || deleting}
                  mr='xs'
                  size='xs'
                  label={intl.formatMessage({ id: 'actions.editFields', defaultMessage: 'Edit fields' })}
                  checked={editing}
                  onChange={(ev) => onEditingChange(ev.currentTarget.checked)}
                />
                <Tooltip label={intl.formatMessage({ id: 'actions.editMetadata', defaultMessage: 'Edit metadata' })} position='left'>
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
                <Tooltip label={intl.formatMessage({ id: 'actions.rematchList', defaultMessage: 'Rematch list' })} position='left'>
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
                <Tooltip label={intl.formatMessage({ id: 'actions.reingestList', defaultMessage: 'Reingest list' })} position='left'>
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
                <Tooltip label={intl.formatMessage({ id: 'actions.deleteList', defaultMessage: 'Delete list' })} position='left'>
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
            </Paper>
          )}
          <Paper withBorder radius='lg'>
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
              }}
            >
              Download list
            </Button>
            <Divider />
            <Button
              onClick={() =>
                handleQidRedirect(import.meta.env.VITE_ALA_BIOCACHE_OCC_SEARCH)
              }
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
              }}
            >
              View occurrence records
            </Button>
            <Divider />
            <Button
              onClick={() =>
                handleQidRedirect(import.meta.env.VITE_ALA_SPATIAL)
              }
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
              }}
            >
              View in spatial portal
            </Button>
          </Paper>
        </Stack>
      </Box>
    </>
  );
}
