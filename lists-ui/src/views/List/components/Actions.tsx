/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useRef, useState } from 'react';
import {
  ActionIcon,
  Box,
  Flex,
  Group,
  Menu,
  Paper,
  Switch,
  Tooltip,
  Text,
  Stack,
  Button,
  Divider,
} from '@mantine/core';
import { DotsThreeIcon } from '@atlasoflivingaustralia/ala-mantine';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEdit, faTrashAlt } from '@fortawesome/free-regular-svg-icons';
import {
  faDownload,
  faFingerprint,
  faGlobe,
  faRefresh,
  faSearch,
  faTableColumns,
} from '@fortawesome/free-solid-svg-icons';

// Mantine Notifications & Modals manager
import { notifications } from '@mantine/notifications';
import { modals } from '@mantine/modals';

// API & Helpers
import { performGQLQuery, SpeciesList, SpeciesListSubmit } from '#/api';
import { useALA } from '#/helpers/context/useALA';

// Local styles
import classes from './Actions.module.css';
import { useNavigate } from 'react-router-dom';
import { getErrorMessage } from '#/helpers';
import { ListMeta } from '#/components/ListMeta';
import { MUTATION_LIST_UPDATE } from '#/api/queries';

interface ActionsProps {
  meta: SpeciesList;
  editing: boolean;
  onEditingChange: (editing: boolean) => void;
  onMetaEdited: (meta: SpeciesListSubmit) => void;
  onRematched: () => void;
}

export function Actions({
  meta,
  editing,
  onEditingChange,
  onMetaEdited,
  onRematched,
}: ActionsProps) {
  const [updating, setUpdating] = useState<boolean>(false);
  const [rematching, setRematching] = useState<boolean>(false);
  const [deleting, setDeleting] = useState<boolean>(false);
  const [fetchingQid, setFetchingQid] = useState<string | null>(null);
  const listQid = useRef<string | null>(null);
  const navigate = useNavigate();

  const ala = useALA();
  const authorisedForList = ala.isAuthorisedForList(meta);

  // Download callback handler
  const handleQidRedirect = useCallback(async (url: string) => {
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
  }, []);

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
  }, []);

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
  }, []);

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
        setRematching(true);
        try {
          // Fire off the delete request
          await ala.rest.lists.rematch(meta.id);
          onRematched();
          notifications.show({
            message: (
              <>
                Rematched <b>{meta.title}</b> successfully
              </>
            ),
            position: 'bottom-left',
            radius: 'md',
          });
        } catch (error) {
          notifications.show({
            message: getErrorMessage(error),
            position: 'bottom-left',
            radius: 'md',
          });
        }
        setRematching(false);
      },
    });
  }, []);

  const handleMetaEdit = useCallback(() => {
    modals.open({
      title: (
        <Text fw='bold' size='lg'>
          Editing {meta.title}
        </Text>
      ),
      size: 'xl',
      children: (
        <ListMeta
          ala={ala}
          initialValues={meta}
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
                    <b>{values.title}</b> updated successfully
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
  }, []);

  return (
    <>
      <Menu shadow='md' width={200} position='bottom-end' radius='lg'>
        <Menu.Target>
          <ActionIcon
            className={classes.mobile}
            variant='light'
            size='md'
            radius='lg'
            aria-label='List actions'
          >
            <DotsThreeIcon />
          </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Label>Actions</Menu.Label>
          <Menu.Item
            onClick={handleDownload}
            disabled={updating || rematching || deleting}
            leftSection={<FontAwesomeIcon icon={faDownload} />}
          >
            Download list
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
            Spatial portal
          </Menu.Item>
          {authorisedForList && (
            <>
              <Menu.Label>Administration</Menu.Label>
              <Menu.Item
                onClick={handleMetaEdit}
                disabled={updating || rematching || deleting}
                leftSection={<FontAwesomeIcon icon={faEdit} />}
              >
                Edit metadata
              </Menu.Item>
              <Menu.Item
                onClick={handleRematch}
                disabled={updating || rematching || deleting}
                color='red'
                leftSection={<FontAwesomeIcon icon={faRefresh} />}
              >
                Rematch list
              </Menu.Item>
              <Menu.Item
                onClick={handleDelete}
                disabled={updating || rematching || deleting}
                color='red'
                leftSection={<FontAwesomeIcon icon={faTrashAlt} />}
              >
                Delete list
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
                    Edit fields
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
          {meta.distinctMatchCount && (
            <Paper
              miw={authorisedForList ? 285 : undefined}
              py={8}
              px='sm'
              shadow='sm'
              radius='lg'
              withBorder
            >
              <Text
                fw='bold'
                style={{
                  textAlign: 'center',
                  fontSize: '0.8rem',
                }}
              >
                <FontAwesomeIcon
                  icon={faFingerprint}
                  style={{ marginRight: 12 }}
                />
                {meta.distinctMatchCount} distinct taxa
              </Text>
            </Paper>
          )}
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
                  label='Edit fields'
                  checked={editing}
                  onChange={(ev) => onEditingChange(ev.currentTarget.checked)}
                />

                <Tooltip label='Download list' position='left'>
                  <ActionIcon
                    onClick={handleDownload}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label='Download list'
                  >
                    <FontAwesomeIcon size='sm' icon={faDownload} />
                  </ActionIcon>
                </Tooltip>
                <Tooltip label='Edit metadata' position='left'>
                  <ActionIcon
                    onClick={handleMetaEdit}
                    disabled={rematching || deleting}
                    loading={updating}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label='Edit metadata'
                  >
                    <FontAwesomeIcon size='sm' icon={faEdit} />
                  </ActionIcon>
                </Tooltip>
                <Tooltip label='Rematch list' position='left'>
                  <ActionIcon
                    onClick={handleRematch}
                    disabled={updating || deleting}
                    loading={rematching}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label='Rematch list'
                  >
                    <FontAwesomeIcon size='sm' icon={faRefresh} />
                  </ActionIcon>
                </Tooltip>
                <Tooltip label='Delete list' position='left'>
                  <ActionIcon
                    onClick={handleDelete}
                    disabled={updating || rematching}
                    loading={deleting}
                    variant='light'
                    size='md'
                    radius='lg'
                    aria-label='Delete list'
                  >
                    <FontAwesomeIcon size='sm' icon={faTrashAlt} />
                  </ActionIcon>
                </Tooltip>
              </Group>
            </Paper>
          )}
          <Paper withBorder radius='lg'>
            {!authorisedForList && (
              <>
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
              </>
            )}
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
                borderTopLeftRadius: authorisedForList ? 14 : 0,
                borderTopRightRadius: authorisedForList ? 14 : 0,
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
