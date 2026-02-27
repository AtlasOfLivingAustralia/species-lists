import { PropsWithChildren, useState } from 'react';
import {
  ActionIcon,
  Box,
  Text,
  TextInput,
  TextInputProps,
  Transition,
} from '@mantine/core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { ThNoWrap } from './ThNoWrap';
import { faTrashAlt } from '@fortawesome/free-regular-svg-icons';

import { TickIcon } from '@atlasoflivingaustralia/ala-mantine';
import { faUndo } from '@fortawesome/free-solid-svg-icons';
import { notifications } from '@mantine/notifications';
import { getErrorMessage } from '#/helpers';
import { performGQLQuery, queries } from '#/api';
import { modals } from '@mantine/modals';

import classes from './ThEditable.module.css';
import inputClasses from '../../classes/TextInput.module.css';

interface ThEditableProps extends PropsWithChildren<TextInputProps> {
  id: string;
  token: string;
  editing: boolean;
  field: string;
  onDelete: () => void;
  onRename: (to: string) => void;
}

export function ThEditable({
  id,
  token,
  editing,
  field,
  onDelete,
  onRename,
}: ThEditableProps) {
  const [updatedField, setUpdatedField] = useState<string>(field);
  const [deleting, setDeleting] = useState<boolean>(false);
  const [updating, setUpdating] = useState<boolean>(false);

  const TRANSITION_MS = 200;
  const ACTIONS_WIDTH_EDITING = 76;
  const ACTIONS_WIDTH_NOT_EDITING = 96;
  const ACTIONS_INNER_WIDTH_EDITING = 78;
  const ACTIONS_INNER_WIDTH_NOT_EDITING = 0;
  const ACTIONS_MARGIN_LEFT_EDITING = 10;
  const ACTIONS_MARGIN_LEFT_NOT_EDITING = 0;
  const ACTIONS_GAP = 10;
  const INPUT_PADDING_EDITING = 10;
  const INPUT_PADDING_NOT_EDITING = 0;
  const TEXTINPUT_MIN_WIDTH_FALLBACK = 100;
  
  const fieldChanged = field !== updatedField;

  // Handler to revert / delete the field
  const handleRevertDelete = async () => {
    if (fieldChanged) {
      setUpdatedField(field);
    } else {
      // Confirm the field  deletion with the user
      modals.openConfirmModal({
        title: (
          <Text fw='bold' size='lg'>
            Confirm field deletion
          </Text>
        ),
        children: (
          <Text>
            Please confirm that you wish to delete the <b>{field}</b> field from
            this list
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
            // Delete the field
            await performGQLQuery(
              queries.MUTATION_LIST_FIELD_DELETE,
              {
                id,
                field,
              },
              token
            );

            // Show success notification
            notifications.show({
              message: (
                <>
                  <b>{field}</b> field deleted successfully
                </>
              ),
              position: 'bottom-left',
              radius: 'md',
            });

            onDelete();
          } catch (error) {
            // Show the error notificaiton
            notifications.show({
              message: getErrorMessage(error as Error),
              position: 'bottom-left',
              radius: 'md',
            });

            setDeleting(false);
          }
        },
      });
    }
  };

  // Handler to rename the field
  const handleRename = async () => {
    // Confirm the field  deletion with the user
    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          Confirm field renaming
        </Text>
      ),
      children: (
        <Text>
          Please confirm that you wish to rename the <b>{field}</b> field to{' '}
          <b>{updatedField}</b> within list
        </Text>
      ),
      labels: { confirm: 'Confirm', cancel: 'Cancel' },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: async () => {
        setUpdating(true);

        try {
          // Delete the field
          await performGQLQuery(
            queries.MUTATION_LIST_FIELD_RENAME,
            {
              id,
              field,
              updatedField,
            },
            token
          );

          // Show success notification
          notifications.show({
            message: (
              <>
                <b>{field}</b> field renamed to <b>{updatedField}</b>{' '}
                successfully
              </>
            ),
            position: 'bottom-left',
            radius: 'md',
          });

          onRename(updatedField);
        } catch (error) {
          // Show the error notificaiton
          notifications.show({
            message: getErrorMessage(error as Error),
            position: 'bottom-left',
            radius: 'md',
          });
        }

        setUpdating(false);
      },
    });
  };

  return (
    <ThNoWrap>
      <TextInput
        type='text'
        placeholder='Field name'
        value={updatedField}
        onChange={(event) => setUpdatedField(event.currentTarget.value)}
        disabled={!editing || updating || deleting}
        classNames={updating || deleting ? undefined : inputClasses}
        miw={CSS.supports('field-sizing', 'content') ? undefined : TEXTINPUT_MIN_WIDTH_FALLBACK}
        styles={{
          input: {
            fieldSizing: 'content',
            transition: `all ease ${TRANSITION_MS}ms`,
            paddingLeft: editing ? INPUT_PADDING_EDITING : INPUT_PADDING_NOT_EDITING,
            paddingRight: editing ? INPUT_PADDING_EDITING : INPUT_PADDING_NOT_EDITING,
          },
        }}
      />
      <div
        style={{
          transition: `all ease ${TRANSITION_MS}ms`,
          width: editing ? ACTIONS_WIDTH_EDITING : ACTIONS_WIDTH_NOT_EDITING,
        }}
      >
        <div
          style={{
            display: 'flex',
            transition: `all ease ${TRANSITION_MS}ms`,
            overflow: 'hidden',
            whiteSpace: 'nowrap',
            width: editing ? ACTIONS_INNER_WIDTH_EDITING : ACTIONS_INNER_WIDTH_NOT_EDITING,
            marginLeft: editing ? ACTIONS_MARGIN_LEFT_EDITING : ACTIONS_MARGIN_LEFT_NOT_EDITING,
            gap: ACTIONS_GAP,
            alignItems: 'center',
          }}
        >
          <ActionIcon
            loading={updating}
            disabled={!fieldChanged || deleting}
            color='grey'
            size='md'
            variant='light'
            radius='md'
            onClick={handleRename}
            aria-label='Rename field'
          >
            <TickIcon size={14} />
          </ActionIcon>
          <ActionIcon
            style={{ transition: `all ${TRANSITION_MS}ms ease` }}
            size='md'
            variant='light'
            radius='md'
            color={fieldChanged ? 'gray' : undefined}
            onClick={handleRevertDelete}
            disabled={updating}
            loading={deleting}
            aria-label={fieldChanged ? 'Revert field' : 'Delete field'}
          >
            <Transition
              mounted={fieldChanged}
              transition='slide-up'
              duration={150}
            >
              {(styles) => (
                <Box component='span' className={classes.undo}>
                  <FontAwesomeIcon style={styles} fontSize={14} icon={faUndo} />
                </Box>
              )}
            </Transition>
            <Box
              component='span'
              className={`${classes.icon} ${
                fieldChanged ? classes.delete : ''
              }`}
            >
              <FontAwesomeIcon fontSize={14} icon={faTrashAlt} />
            </Box>
          </ActionIcon>
        </div>
      </div>
    </ThNoWrap>
  );
}
