import { Container, Grid, Text } from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import {
  faArrowsRotate,
  faIdCard,
  faRightLeft,
} from '@fortawesome/free-solid-svg-icons';
import { Navigate } from 'react-router-dom';

// Local components
import { ActionCard } from './components/ActionCard';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

export function Component() {
  useDocumentTitle('ALA Lists | Admin');

  const ala = useALA();
  console.log(ala.isAdmin);

  if (!ala.isAdmin) return <Navigate to='/' />;

  const handleClick = (action: string, verb: string) => {
    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          Confirm {verb}
        </Text>
      ),
      children: (
        <Text>Please confirm that you wish to {action} all species lists</Text>
      ),
      labels: { confirm: 'Confirm', cancel: 'Cancel' },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: async () => {
        try {
          switch (action) {
            case 'reindex':
              await ala.rest.admin?.reindex();
              break;
            case 'rematch':
              await ala.rest.admin?.rematch();
              break;
            case 'migrate':
              await ala.rest.admin?.migrate();
              break;
          }

          // Show success notification
          notifications.show({
            message: `${verb} started successfully`,
            position: 'bottom-left',
            radius: 'md',
          });
        } catch (error) {
          console.log('admin error', error);
          // Show error notification
          notifications.show({
            message: getErrorMessage(error),
            position: 'bottom-left',
            radius: 'md',
          });
        }
      },
    });
  };

  return (
    <Container fluid>
      <Grid>
        <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 3 }}>
          <ActionCard
            title='Reindex'
            description='Regenerate the elastic search index for all lists'
            icon={faArrowsRotate}
            onClick={() => handleClick('reindex', 'Reindexing')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 3 }}>
          <ActionCard
            title='Rematch'
            description='Rematch the taxonomy for all lists'
            icon={faIdCard}
            onClick={() => handleClick('rematch', 'Rematching')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 3 }}>
          <ActionCard
            title='Migrate'
            description='Migrate lists from the legacy lists tool'
            icon={faRightLeft}
            onClick={() => handleClick('migrate', 'Migration')}
          />
        </Grid.Col>
      </Grid>
    </Container>
  );
}

Object.assign(Component, { displayName: 'Admin' });
