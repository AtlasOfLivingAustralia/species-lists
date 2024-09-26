/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useState } from 'react';
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
} from '@mantine/core';
import { DotsThreeIcon } from '@atlasoflivingaustralia/ala-mantine';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faEdit, faTrashAlt } from '@fortawesome/free-regular-svg-icons';
import { faRefresh, faTableColumns } from '@fortawesome/free-solid-svg-icons';

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
}

export function Actions({
  meta,
  editing,
  onEditingChange,
  onMetaEdited,
}: ActionsProps) {
  const [updating, setUpdating] = useState<boolean>(false);
  const [rematching, setRematching] = useState<boolean>(false);
  const [deleting, setDeleting] = useState<boolean>(false);
  const navigate = useNavigate();

  const ala = useALA();
  const authorisedForList = ala.isAuthorisedForList(meta);

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
              console.log(ala.token);
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
            disabled={!authorisedForList}
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
        </Menu.Dropdown>
      </Menu>
      <Box className={classes.desktop}>
        <Paper miw={226} py={8} px='sm' shadow='sm' radius='lg' withBorder>
          <Group className={classes.desktop} gap='md'>
            <Switch
              disabled={
                !authorisedForList || updating || rematching || deleting
              }
              size='xs'
              label='Edit fields'
              checked={editing}
              onChange={(ev) => onEditingChange(ev.currentTarget.checked)}
            />
            <Tooltip label='Edit metadata' position='left'>
              <ActionIcon
                onClick={handleMetaEdit}
                disabled={!authorisedForList || rematching || deleting}
                loading={updating}
                variant='light'
                size='lg'
                radius='lg'
                aria-label='Edit metadata'
              >
                <FontAwesomeIcon icon={faEdit} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label='Rematch list' position='left'>
              <ActionIcon
                onClick={handleRematch}
                disabled={!authorisedForList || updating || deleting}
                loading={rematching}
                variant='light'
                size='lg'
                radius='lg'
                aria-label='Rematch list'
              >
                <FontAwesomeIcon icon={faRefresh} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label='Delete list' position='left'>
              <ActionIcon
                onClick={handleDelete}
                disabled={!authorisedForList || updating || rematching}
                loading={deleting}
                variant='light'
                size='lg'
                radius='lg'
                aria-label='Delete list'
              >
                <FontAwesomeIcon icon={faTrashAlt} />
              </ActionIcon>
            </Tooltip>
          </Group>
        </Paper>
      </Box>
    </>
  );
}
