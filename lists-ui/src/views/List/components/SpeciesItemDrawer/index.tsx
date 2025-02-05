/* eslint-disable @typescript-eslint/no-explicit-any */
import { useEffect, useState } from 'react';
import { useRouteLoaderData } from 'react-router';
import {
  InputSpeciesList,
  performGQLQuery,
  queries,
  SpeciesList,
  SpeciesListItem,
} from '#/api';
import {
  Box,
  Button,
  Center,
  Divider,
  Drawer,
  Flex,
  Group,
  Paper,
  ScrollArea,
  Stack,
  Switch,
  Text,
  Title,
} from '@mantine/core';

// Notifications & modals managers
import { notifications } from '@mantine/notifications';
import { modals } from '@mantine/modals';

import classes from './index.module.css';
import { TaxonImage } from '#/components/TaxonImage';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

// Display & edit components
import { Display } from './components/Display';
import { Edit } from './components/Edit';
import { Create } from './components/Create';

interface SpeciesItemDrawerProps {
  item: SpeciesListItem | null;
  opened: boolean;
  onClose: () => void;
  onEdited: (item: SpeciesListItem) => void;
  onDeleted: (id: string) => void;
}

export function SpeciesItemDrawer({
  item,
  opened,
  onClose,
  onEdited,
  onDeleted,
}: SpeciesItemDrawerProps) {
  const [updating, setUpdating] = useState<boolean>(false);
  const [editing, setEditing] = useState<boolean>(false);
  const [editItem, setEditItem] = useState<InputSpeciesList | null>(null);
  const { meta } = useRouteLoaderData('list') as { meta: SpeciesList };
  const { auth, isAuthorisedForList } = useALA();

  // Reset the editing status when the supplied item changes
  useEffect(() => {
    setUpdating(false);
    setEditing(false);
    setEditItem(null);
  }, [item]);

  // // Clear edit item when finished editing
  useEffect(() => {
    if (!editing) setEditItem(null);
  }, [editing]);

  const onItemUpdate = async () => {
    setUpdating(true);

    try {
      const { newItem } = await performGQLQuery<{ newItem: SpeciesListItem }>(
        queries.MUTATION_LIST_ITEM_UPDATE,
        {
          editItem,
        },
        auth?.user?.access_token
      );

      if (newItem) onEdited(newItem);
      setEditing(false);
    } catch (error) {
      notifications.show({
        message: getErrorMessage(error as Error),
        position: 'bottom-left',
        radius: 'md',
      });
    }

    setUpdating(false);
  };

  const onItemCreate = async () => {
    setUpdating(true);

    try {
      const { newItem } = await performGQLQuery<{ newItem: SpeciesListItem }>(
        queries.MUTATION_LIST_ITEM_CREATE,
        {
          createItem: {
            ...editItem,
            speciesListID: meta.id,
          },
        },
        auth?.user?.access_token
      );

      if (newItem) onEdited(newItem);
      setEditing(false);
    } catch (error) {
      notifications.show({
        message: getErrorMessage(error as Error),
        position: 'bottom-left',
        radius: 'md',
      });
    }

    setUpdating(false);
  };

  const onItemDelete = async () => {
    if (!item) return;

    setUpdating(true);

    try {
      await performGQLQuery<{ newItem: SpeciesListItem }>(
        queries.MUTATION_LIST_ITEM_DELETE,
        {
          id: item.id,
        },
        auth?.user?.access_token
      );

      onDeleted(item.id);
    } catch (error) {
      notifications.show({
        message: getErrorMessage(error as Error),
        position: 'bottom-left',
        radius: 'md',
      });

      setUpdating(false);
    }
  };

  return (
    <Drawer.Root
      offset={16}
      radius='lg'
      size='lg'
      opened={opened}
      onClose={() => {
        if (!updating) onClose();
      }}
      position='right'
      scrollAreaComponent={ScrollArea.Autosize}
      aria-label='Species details'
      role='dialog'
    >
      <Drawer.Overlay />
      <Drawer.Content
        style={{ overflowX: 'hidden' }}
        aria-label='Species details'
      >
        <Drawer.Header>
          <Flex justify='space-between' align='center' w='100%'>
            <Title fs='italic' order={4} mr='xs'>
              {item ? item.scientificName : 'Add species'}
            </Title>
            <Flex align='center'>
              {item && (
                <Switch
                  disabled={!isAuthorisedForList(meta)}
                  checked={editing}
                  onChange={(event) => setEditing(event.currentTarget.checked)}
                  labelPosition='left'
                  size='xs'
                  label={auth?.isAuthenticated ? 'Edit' : 'Sign in to edit'}
                  miw={auth?.isAuthenticated ? undefined : 120}
                />
              )}
              <Drawer.CloseButton
                ml='xs'
                radius='lg'
                mx={0}
                aria-label='Close species details'
              />
            </Flex>
          </Flex>
        </Drawer.Header>
        <Drawer.Body p={0}>
          {item && (
            <Box className={classes.imageBackground}>
              <Center>
                <TaxonImage
                  taxonID={item?.classification?.taxonConceptID}
                  h={200}
                  w='100%'
                />
              </Center>
            </Box>
          )}
          <Stack p='md' gap='lg'>
            {item ? (
              editing ? (
                <Edit
                  onItemUpdated={(updatedItem) => setEditItem(updatedItem)}
                  item={item}
                />
              ) : (
                <Display item={item} />
              )
            ) : (
              <Create
                onItemUpdated={(updatedItem) => setEditItem(updatedItem)}
              />
            )}
          </Stack>
        </Drawer.Body>
        <Paper
          className={classes.actions}
          style={{
            height: editing || !item ? 60 : 0,
          }}
        >
          <Divider />
          {item ? (
            <Group justify='space-between' px='sm' py='sm'>
              <Group gap='xs'>
                <Button
                  disabled={!editItem}
                  loading={updating}
                  onClick={onItemUpdate}
                  radius='md'
                  variant='filled'
                >
                  Update
                </Button>
                <Button
                  disabled={updating}
                  radius='md'
                  variant='light'
                  color='grey'
                  onClick={() => setEditing(false)}
                >
                  Cancel
                </Button>
              </Group>
              <Button
                disabled={updating}
                onClick={() =>
                  modals.openConfirmModal({
                    title: (
                      <Text fw='bold' size='lg'>
                        Confirm item deletion
                      </Text>
                    ),
                    children: (
                      <Text>
                        Please confirm that you wish to delete{' '}
                        <b>{item?.scientificName}</b> from this list.
                      </Text>
                    ),
                    labels: { confirm: 'Confirm', cancel: 'Cancel' },
                    confirmProps: {
                      variant: 'filled',
                      radius: 'md',
                    },
                    cancelProps: { radius: 'md' },
                    onConfirm: onItemDelete,
                  })
                }
                radius='md'
                variant='outline'
              >
                Remove from list
              </Button>
            </Group>
          ) : (
            <Group px='sm' py='sm' gap='xs'>
              <Button
                disabled={!editItem}
                loading={updating}
                onClick={onItemCreate}
                radius='md'
                variant='filled'
              >
                Create
              </Button>
              <Button
                disabled={updating}
                radius='md'
                variant='light'
                color='grey'
                onClick={() => onClose()}
              >
                Cancel
              </Button>
            </Group>
          )}
        </Paper>
      </Drawer.Content>
    </Drawer.Root>
  );
}
