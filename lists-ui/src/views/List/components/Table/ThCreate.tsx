import { ActionIcon, Text, TextInput } from '@mantine/core';
import { PlusIcon } from '@atlasoflivingaustralia/ala-mantine';

// Mantine Notifications / Modals helpers
import { notifications } from '@mantine/notifications';
import { modals } from '@mantine/modals';

// Local components
import { ThNoWrap } from './ThNoWrap';
import { useRef, useState } from 'react';
import { performGQLQuery, queries } from '#/api';
import { getErrorMessage } from '#/helpers';

interface ThCreateProps {
  id: string;
  token: string;
  onCreate: (field: string, defaultValue?: string) => void;
}

export function ThCreate({ id, token, onCreate }: ThCreateProps) {
  const [creating, setCreating] = useState<boolean>(false);
  const [newField, setNewField] = useState<string>('');
  const defaultRef = useRef<HTMLInputElement>(null);

  // Handler to create the field
  const handleCreate = async () => {
    // Confirm the field creation with the user
    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          Confirm field creation
        </Text>
      ),
      children: (
        <>
          <Text>
            Please confirm that you wish to create the <b>{newField}</b> field
            for this list, and optionally, provide a default value
          </Text>
          <TextInput
            ref={defaultRef}
            mt='md'
            placeholder='Default value'
            data-autofocus
          />
        </>
      ),
      labels: { confirm: 'Confirm', cancel: 'Cancel' },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: async () => {
        try {
          const defaultValue = defaultRef.current?.value || undefined;

          // Create the field
          await performGQLQuery(
            queries.MUTATION_LIST_FIELD_CREATE,
            {
              id,
              fieldName: newField,
              fieldValue: defaultValue,
            },
            token
          );

          // Reset the newField value
          setNewField('');

          // Show success notification
          notifications.show({
            message: (
              <>
                <b>{newField}</b> field created successfully
              </>
            ),
            position: 'bottom-left',
            radius: 'md',
          });

          onCreate(newField, defaultValue);
        } catch (error) {
          // Show the error notificaiton
          notifications.show({
            message: getErrorMessage(error as Error),
            position: 'bottom-left',
            radius: 'md',
          });

          setCreating(false);
        }
      },
    });
  };

  return (
    <ThNoWrap>
      <TextInput
        disabled={creating}
        value={newField}
        onChange={(ev) => setNewField(ev.currentTarget.value)}
        miw={85}
        placeholder='Add field'
        styles={{
          input: {
            fieldSizing: 'content',
          },
        }}
        mr='xs'
      />
      <ActionIcon
        color='grey'
        size='md'
        variant='light'
        radius='md'
        onClick={handleCreate}
        loading={creating}
        disabled={newField.length < 1}
      >
        <PlusIcon size={12} />
      </ActionIcon>
    </ThNoWrap>
  );
}
