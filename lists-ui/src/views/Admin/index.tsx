import {
  faArrowsRotate,
  faCode,
  faIdCard,
  faRefresh,
  faRightLeft,
  faSearch,
  faTrash,
  faUser,
  faWarning,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Alert,
  Badge,
  Center,
  Container,
  Flex,
  Grid,
  Group,
  Image,
  Paper,
  Progress,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { useDocumentTitle, useMounted } from '@mantine/hooks';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { useCallback, useEffect, useState } from 'react';
import { FormattedMessage, useIntl } from 'react-intl';
import { Navigate, useLoaderData, useNavigate } from 'react-router';

// Local components
import { MigrateProgress } from '#/api';
import { IngestProgress } from '#/components/IngestProgress';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';
import { Breadcrumbs } from '../Dashboard/components/Breadcrumbs';
import { ActionCard } from './components/ActionCard';
import { FetchInfo } from './components/FetchInfo';

// Very important warning image
import warningImage from '#/static/warning.gif';
import classes from './index.module.css';

export function Component() {
  useDocumentTitle('ALA Lists | Admin');
  const intl = useIntl();

  // State hooks
  const migrationLoader = useLoaderData();
  const [migrationDisabled, setMigrationDisabled] = useState<boolean>(
    Boolean(migrationLoader)
  );
  const [migrationProgress, setMigrationProgress] =
    useState<MigrateProgress | null>(migrationLoader);

  // Function hooks
  const ala = useALA();
  const navigate = useNavigate();
  const mounted = useMounted();

  if (!ala.isAdmin) return <Navigate to='/' />;

  // Watch migration progress
  useEffect(() => {
    async function checkMigrationProgress() {
      try {
        const progress = await ala.rest.admin!.migrateProgress();
        setMigrationProgress(progress);
        // Uncomment the following lines to reset if server was restarted during migration
        // if (progress == null || progress?.started == null) {
        //   setMigrationProgress(null);
        //   setMigrationDisabled(false);
        // }
      } catch (error) {
        console.log('admin error', error);
        // Show error notification
        notifications.show({
          message: getErrorMessage(error),
          position: 'bottom-left',
          radius: 'md',
        });
      }
    }

    // If migration has started, continually check it
    if (ala.rest.admin && migrationProgress) {
      setTimeout(checkMigrationProgress, 10000);
    }
  }, [migrationProgress, ala]);

  // Start polling for migration progress to appear
  const handleMigrationStart = useCallback(async () => {
    setMigrationDisabled(true);
    const check = async () => {
      try {
        const progress = await ala.rest.admin!.migrateProgress();
        if (progress) {
          setMigrationProgress(progress);
        } else {
          setTimeout(check, 2000);
        }
      } catch (error) {
        console.log('Migration start error', error);
      }
    };

    setTimeout(check, 5000);
  }, [ala]);

  // Show warning message on page navigation
  useEffect(() => {
    if (mounted) {
      modals.openConfirmModal({
        title: (
          <Group>
            <FontAwesomeIcon icon={faWarning} />
            <Text fw='bold' size='lg'>
              Proceed with caution
            </Text>
          </Group>
        ),
        children: (
          <Stack>
            <Center>
              <Image height={200} src={warningImage} />
            </Center>
            <Text mt='md'>
              Please only trigger any these functions during low usage.
            </Text>
            <Text fw='bold'>Please click confirm to acknowledge this.</Text>
          </Stack>
        ),
        labels: { confirm: 'Confirm', cancel: 'Cancel' },
        confirmProps: {
          variant: 'filled',
          radius: 'md',
        },
        cancelProps: { radius: 'md' },
        onCancel: () => navigate('/'),
        closeOnClickOutside: false,
        withCloseButton: false,
        transitionProps: {
          transition: 'pop',
        },
      });
    }
  }, [mounted]);

  const handleClick = useCallback(
    (action: string, verb: string) => {
      if (migrationProgress !== null) return;

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
                handleMigrationStart();
                await ala.rest.admin?.reindex();
                break;
              case 'rematch':
                handleMigrationStart();
                await ala.rest.admin?.rematch();
                break;
              case 'migrate-all':
                handleMigrationStart();
                await ala.rest.admin?.migrate();
                break;
              case 'migrate-custom':
                const query = prompt(
                  'Please enter your custom query (i.e. /ws/speciesList?isAuthoritative=eq:true)'
                );
                if (query) {
                  handleMigrationStart();
                  await ala.rest.admin?.migrateCustom(
                    decodeURIComponent(query)
                  );
                } else {
                  cancelSucessNotification = true;
                }
                break;
              case 'migrate-userdetails':
                handleMigrationStart();
                await ala.rest.admin?.migrateUserdetails();
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
    },
    [ala, migrationProgress]
  );

  return (
    <>
      <Container fluid className={classes.speciesHeader}>
        <Grid>
          <Grid.Col span={12}>
            <Breadcrumbs listTitle={intl.formatMessage({ id: 'admin.title', defaultMessage: 'Admin' })}/>
          </Grid.Col>
          <Grid.Col span={12}>
            <Title order={3} classNames={{ root: classes.title }} >
              <FormattedMessage id='admin.title.label' defaultMessage='Admin Functions' />
            </Title>
          </Grid.Col>
        </Grid>
      </Container>
      <Container fluid>
        <Stack gap='xl' mt='lg'>
          <Grid>
            <Grid.Col span={12}>
              <Title order={4}>Migration</Title>
            </Grid.Col>
            <Grid.Col span={12}>
              <Stack>
                {migrationProgress && (
                  <Paper p='md' radius='lg' withBorder>
                    <Stack>
                      <Flex justify='space-between'>
                        <Text>
                          {migrationProgress.currentSpeciesList ? (
                            <>
                              <b>Migrating: </b>{' '}
                              {migrationProgress.currentSpeciesList.title}
                            </>
                          ) : (
                            'Starting migration'
                          )}
                        </Text>
                        <Badge miw={80} ml='xs'>
                          {migrationProgress.completed}/{migrationProgress.total}
                        </Badge>
                      </Flex>
                      <Progress
                        value={
                          (migrationProgress.completed /
                            migrationProgress.total) *
                          100
                        }
                      />
                    </Stack>
                  </Paper>
                )}
                <IngestProgress
                  id={migrationProgress?.currentSpeciesList?.id || null}
                  ingesting={Boolean(migrationProgress)}
                  disableNavigation
                />
              </Stack>
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                disabled={Boolean(migrationProgress) || migrationDisabled}
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
            {['56599'].includes(ala.userid) && (
              <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
                <ActionCard
                  disabled={Boolean(migrationProgress) || migrationDisabled}
                  title='Custom'
                  description={
                    <>
                      Migrate lists from the legacy lists tool{' '}
                      <b>using a custom query</b>
                    </>
                  }
                  icon={faCode}
                  onClick={() =>
                    handleClick('migrate-custom', 'Custom Migration')
                  }
                />
              </Grid.Col>
            )}
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                disabled={Boolean(migrationProgress) || migrationDisabled}
                title='Userdetails'
                description={
                  <>Migrate user data from userdetails for legacy lists</>
                }
                icon={faUser}
                onClick={() =>
                  handleClick('migrate-userdetails', 'Userdetails Migration')
                }
              />
            </Grid.Col>
          </Grid>
          <Grid>
            <Grid.Col span={12}>
              <Stack>
                <Title order={4}>Tools</Title>
                <Text c='dimmed'>
                  Please use the following tools <b>with caution</b> and{' '}
                  <b>verify</b> that these really need to be run first.
                </Text>
              </Stack>
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                disabled={Boolean(migrationProgress) || migrationDisabled}
                title='Rematch'
                description='Rematch the taxonomy for all lists'
                icon={faIdCard}
                onClick={() => handleClick('rematch', 'Rematching')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                disabled={Boolean(migrationProgress) || migrationDisabled}
                title='Reindex'
                description='Regenerate the elastic search index for all lists'
                icon={faArrowsRotate}
                onClick={() => handleClick('reindex', 'Reindexing')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                title='Mongo indexes'
                description='View indexed fields for MongoDB collections'
                icon={faSearch}
                onClick={() => {
                  modals.open({
                    size: 'xl',
                    title: (
                      <Text fw='bold' size='lg'>
                        Mongo Indexes
                      </Text>
                    ),
                    children: <FetchInfo fetcher={ala.rest.admin?.indexes} />,
                  });
                }}
              />
            </Grid.Col>
          </Grid>
          {['testing', 'development'].includes(import.meta.env.MODE) && (
            <Grid>
              <Grid.Col span={12}>
                <Stack>
                  <Title order={4}>Danger Zone</Title>
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
                  disabled={Boolean(migrationProgress) || migrationDisabled}
                  title='Delete index'
                  description='Clear all data from the Elasticsearch index (requires reboot after running)'
                  icon={faTrash}
                  onClick={() => handleClick('wipe-index', 'Index Deletion')}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
                <ActionCard
                  disabled={Boolean(migrationProgress) || migrationDisabled}
                  title='Delete docs'
                  description='Clear all data from the MongoDB collections (requires reimport)'
                  icon={faTrash}
                  onClick={() => handleClick('wipe-docs', 'Document Deletion')}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
                <ActionCard
                  disabled={Boolean(migrationProgress) || migrationDisabled}
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
    </>
  );
}

Object.assign(Component, { displayName: 'Admin' });
