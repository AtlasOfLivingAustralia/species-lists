import {
  Alert,
  Badge,
  Container,
  Grid,
  Group,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faArrowsRotate,
  faCode,
  faIdCard,
  faRefresh,
  faRightLeft,
  faShield,
  faTrash,
  faWarning,
} from '@fortawesome/free-solid-svg-icons';
import { Navigate } from 'react-router-dom';

// Local components
import { ActionCard } from './components/ActionCard';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

export function Component() {
  useDocumentTitle('ALA Lists | Admin');

  const ala = useALA();

  if (!ala.isAdmin) return <Navigate to='/' />;

  const handleClick = (action: string, verb: string) => {
    modals.openConfirmModal({
      title: (
        <Text fw='bold' size='lg'>
          Confirm {verb}
        </Text>
      ),
      children: (
        <Stack>
          <Text>Please confirm that you wish to perform this action</Text>
          {action.startsWith('wipe') && (
            <Text fw='bold' c='rust'>
              This deletion will be irreversible!
            </Text>
          )}
        </Stack>
      ),
      labels: { confirm: 'Confirm', cancel: 'Cancel' },
      confirmProps: {
        variant: 'filled',
        radius: 'md',
      },
      cancelProps: { radius: 'md' },
      onConfirm: async () => {
        let cancelSucessNotification = false;
        try {
          switch (action) {
            case 'reindex':
              await ala.rest.admin?.reindex();
              break;
            case 'rematch':
              await ala.rest.admin?.rematch();
              break;
            case 'migrate-all':
              await ala.rest.admin?.migrate('all');
              break;
            case 'migrate-authoritative':
              await ala.rest.admin?.migrate('authoritative');
              break;
            case 'migrate-custom':
              const query = prompt(
                'Please enter your custom query (i.e. /ws/speciesList?isAuthoritative=eq:true)'
              );
              if (query) {
                await ala.rest.admin?.migrateCustom(decodeURIComponent(query));
              } else {
                cancelSucessNotification = true;
              }
              break;
            case 'wipe-index':
              await ala.rest.admin?.wipe('index');
              break;
            case 'wipe-docs':
              await ala.rest.admin?.wipe('docs');
              break;
            case 'reboot':
              await ala.rest.admin?.reboot();
              break;
          }

          if (!cancelSucessNotification) {
            // Show success notification
            notifications.show({
              message: `${verb} started successfully`,
              position: 'bottom-left',
              radius: 'md',
            });
          }
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
      <Stack gap='xl'>
        <Grid>
          <Grid.Col span={12}>
            <Group>
              <Badge variant='light'>Step 1</Badge>
              <Title order={4}>Migrate Data</Title>
            </Group>
          </Grid.Col>
          <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
            <ActionCard
              title='All'
              description={
                <>
                  Migrate <b>all</b> lists from the legacy lists tool
                </>
              }
              icon={faRightLeft}
              onClick={() => handleClick('migrate-all', 'All Migration')}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
            <ActionCard
              title='Authoritative'
              description={
                <>
                  Migrate <b>authoritative</b> lists from the legacy lists tool
                </>
              }
              icon={faShield}
              onClick={() =>
                handleClick('migrate-authoritative', 'Authoritative Migration')
              }
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
            <ActionCard
              title='Custom'
              description={
                <>
                  Migrate lists from the legacy lists tool{' '}
                  <b>using a custom query</b>
                </>
              }
              icon={faCode}
              onClick={() => handleClick('migrate-custom', 'Custom Migration')}
            />
          </Grid.Col>
        </Grid>
        <Grid>
          <Grid.Col span={12}>
            <Group>
              <Badge variant='light'>Step 2</Badge>
              <Title order={4}>Rematch</Title>
            </Group>
          </Grid.Col>
          <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
            <ActionCard
              title='Rematch'
              description='Rematch the taxonomy for all lists'
              icon={faIdCard}
              onClick={() => handleClick('rematch', 'Rematching')}
            />
          </Grid.Col>
        </Grid>
        <Grid>
          <Grid.Col span={12}>
            <Group>
              <Badge variant='light'>Step 3</Badge>
              <Title order={4}>Reindex</Title>
            </Group>
          </Grid.Col>
          <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
            <ActionCard
              title='Reindex'
              description='Regenerate the elastic search index for all lists'
              icon={faArrowsRotate}
              onClick={() => handleClick('reindex', 'Reindexing')}
            />
          </Grid.Col>
        </Grid>
        {['testing', 'development'].includes(import.meta.env.MODE) && (
          <Grid>
            <Grid.Col span={12}>
              <Stack>
                <Title order={4}>Tools</Title>
                <Alert
                  icon={<FontAwesomeIcon icon={faWarning} color='white' />}
                  title='Danger Zone'
                  variant='filled'
                  radius='md'
                >
                  Extremely massive danger zone here.{' '}
                  <b>Run these ONLY in development/testing environments.</b>
                </Alert>
              </Stack>
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                title='Delete index'
                description='Clear all data from the Elasticsearch index'
                icon={faTrash}
                onClick={() => handleClick('wipe-index', 'Index Deletion')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                title='Delete docs'
                description='Clear all data from the Elasticsearch index'
                icon={faTrash}
                onClick={() => handleClick('wipe-docs', 'Document Deletion')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                title='Reboot'
                description='Reboot the lists application'
                icon={faRefresh}
                onClick={() => handleClick('reboot', 'Reboot')}
              />
            </Grid.Col>
          </Grid>
        )}
      </Stack>
    </Container>
  );
}

Object.assign(Component, { displayName: 'Admin' });
