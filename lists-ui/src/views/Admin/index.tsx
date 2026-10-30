import {
  faArrowsRotate,
  faCode,
  faIdCard,
  faRefresh,
  faRightLeft,
  faSearch,
  faTrash,
  faWarning,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Alert,
  Badge,
  Container,
  Flex,
  Grid,
  Group,
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
import PageLoader from '#/components/PageLoader';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';
import { Breadcrumbs } from '../Dashboard/components/Breadcrumbs';
import { ActionCard } from './components/ActionCard';
import { FetchInfo } from './components/FetchInfo';

import classes from './index.module.css';

export function Component() {
  useDocumentTitle('ALA Lists | Admin');
  const intl = useIntl();
  const AppVersion = () => <span>v{__APP_VERSION__}</span>

  // State hooks
  const migrationLoader = useLoaderData() as MigrateProgress | undefined;
  const [initialLoading, setInitialLoading] = useState<boolean>(
    migrationLoader === undefined
  );
  const [migrationDisabled, setMigrationDisabled] = useState<boolean>(
    migrationLoader !== undefined ? Boolean(migrationLoader) : true
  );
  const [migrationProgress, setMigrationProgress] =
    useState<MigrateProgress | null>(migrationLoader || null);

  // Function hooks
  const ala = useALA();
  const navigate = useNavigate();
  const mounted = useMounted();
  const [runAction, setRunAction] = useState<string>('');

  // Watch migration progress
  useEffect(() => {
    async function checkMigrationProgress() {
      try {
        const progress = await ala.rest.admin!.migrateProgress();
        setMigrationProgress(progress);
        setMigrationDisabled(Boolean(progress));
      } catch (error) {
        console.log('admin error', error);
        // Show error notification
        notifications.show({
          message: getErrorMessage(error),
          position: 'bottom-left',
          radius: 'md',
        });
      } finally {
        setInitialLoading(false);
      }
    }

    // Check migration progress initially on mount, and continually poll if running
    if (ala.rest.admin) {
      if (migrationProgress) {
        const timer = setTimeout(checkMigrationProgress, 10000);
        return () => clearTimeout(timer);
      } else {
        checkMigrationProgress();
      }
    }
  }, [migrationProgress, ala]);

  // Start polling for migration progress to appear
  const handleMigrationStart = useCallback(async () => {
    setMigrationDisabled(true);
    let attempts = 0;
    const maxAttempts = 5;
    const check = async () => {
      try {
        const progress = await ala.rest.admin!.migrateProgress();
        if (progress) {
          setMigrationProgress(progress);
        } else if (attempts < maxAttempts) {
          attempts++;
          setTimeout(check, 2000);
        } else {
          // Migration completed with nothing to import
          setMigrationDisabled(false);
          notifications.show({
            message: 'Migration completed with no new lists to import. All lists are already up to date.',
            position: 'bottom-left',
            radius: 'md',
          });
        }
      } catch (error) {
        console.log('Migration start error', error);
        setMigrationDisabled(false);
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
              <FormattedMessage
                id='admin.confirm.caution.title'
                defaultMessage='Proceed with caution'
              />
            </Text>
          </Group>
        ),
        children: (
          <Stack>
            <Text mt='md'>
              <FormattedMessage
                id='admin.confirm.caution.body'
                defaultMessage='Please only trigger these functions during low usage.'
              />
            </Text>
            <Text fw='bold'>
              <FormattedMessage
                id='admin.confirm.caution.acknowledge'
                defaultMessage='Please click confirm to acknowledge this.'
              />
            </Text>
          </Stack>
        ),
        labels: {
          confirm: intl.formatMessage({ id: 'listmeta.button.confirm.label', defaultMessage: 'Confirm' }),
          cancel: intl.formatMessage({ id: 'listmeta.button.cancel.label', defaultMessage: 'Cancel' }),
        },
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
  }, [mounted, intl]);

  const handleClick = useCallback(
    (action: string) => {
      setRunAction(action);
      const actionLabel = intl.formatMessage({
        id: `admin.${action}`,
        defaultMessage: action,
      });
      modals.openConfirmModal({
        title: (
          <Text fw='bold' size='lg'>
            <FormattedMessage
              id='admin.confirm.title'
              defaultMessage='Confirm: {action}'
              values={{ action: actionLabel }}
            />
          </Text>
        ),
        children: (
          <Stack>
            <Text>
              <FormattedMessage
                id='admin.confirm.body'
                defaultMessage='Please confirm that you wish to perform this action'
              />
            </Text>
            {action.startsWith('wipe') && (
              <Text fw='bold' c='rust'>
                <FormattedMessage
                  id='admin.confirm.irreversible'
                  defaultMessage='This deletion will be irreversible!'
                />
              </Text>
            )}
          </Stack>
        ),
        labels: {
          confirm: intl.formatMessage({ id: 'listmeta.button.confirm.label', defaultMessage: 'Confirm' }),
          cancel: intl.formatMessage({ id: 'listmeta.button.cancel.label', defaultMessage: 'Cancel' }),
        },
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
              case 'migrate-reset':
                await ala.rest.admin?.migrateReset();
                setMigrationDisabled(false);
                setMigrationProgress(null);
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
                message: intl.formatMessage(
                  { id: 'admin.started', defaultMessage: '{action} started successfully' },
                  { action: actionLabel }
                ),
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
    [ala, intl]
  );

  if (!ala.isAdmin) {
    return <Navigate to='/' replace />;
  }

  if (initialLoading) {
    return <PageLoader />;
  }

  return (
    <>
      <Container fluid className={classes.speciesHeader}>
        <Grid>
          <Grid.Col span={12}>
            <Breadcrumbs listTitle={intl.formatMessage({ id: 'admin.title', defaultMessage: 'Admin' })}/>
          </Grid.Col>
          <Grid.Col span={6}>
            <Title order={3} classNames={{ root: classes.title }} >
              <FormattedMessage id='admin.title.label' defaultMessage='Admin functions' />
            </Title>
          </Grid.Col>
          <Grid.Col span={6} ta='right'>
            <Title order={4} classNames={{ root: classes.title }} >
               <FormattedMessage id='admin.appVersion' defaultMessage='App version' />: <AppVersion />
            </Title>
          </Grid.Col>
        </Grid>
      </Container>
      <Container fluid>
        <Stack gap='xl' mt='lg'>
         <Grid>
            <Grid.Col span={12}>
              <Stack>
                <Title order={4}>
                  <FormattedMessage
                    id='admin.banner.title'
                    defaultMessage='Banner Message'
                  />
                </Title>
                <Text>
                  <FormattedMessage
                    id='admin.banner.description'
                    defaultMessage='Use the <a>ALA global admin tool</a> to manage banner messages displayed to users. Use the "Lists" entry to show message for this app and "Global" to show on all ALA apps.'
                    values={{
                      a: (chunks) => (
                        <a
                          href={
                            import.meta.env.VITE_ALA_GLOBAL_ADMIN ||
                            'https://admin.ala.org.au/banners'
                          }
                          target='_blank'
                          rel='noopener noreferrer'
                        >
                          {chunks}
                        </a>
                      ),
                    }}
                  />
                </Text>
              </Stack>
            </Grid.Col>
          </Grid>
          <Grid>
            <Grid.Col span={12}>
              <Title order={4}><FormattedMessage id='admin.migration.title' defaultMessage='Migration' /></Title>
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
                              <b><FormattedMessage id={`admin.${runAction}`} defaultMessage="Migrating" />: </b>{' '}
                              {migrationProgress.currentSpeciesList.title}
                            </>
                          ) : (
                            intl.formatMessage(
                              { id: 'admin.progress.starting', defaultMessage: 'Starting {action}' },
                              { action: intl.formatMessage({ id: `admin.${runAction}`, defaultMessage: 'Migrating' }) }
                            )
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
                title={intl.formatMessage({ id: 'admin.card.migrateAll.title', defaultMessage: 'All lists' })}
                description={
                  <FormattedMessage
                    id='admin.card.migrateAll.description'
                    defaultMessage='Migrate <b>all</b> lists from the legacy lists tool'
                    values={{ b: (chunks) => <b>{chunks}</b> }}
                  />
                }
                icon={faRightLeft}
                onClick={() => handleClick('migrate-all')}
              />
            </Grid.Col>
              <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
                <ActionCard
                  disabled={!(Boolean(migrationProgress) || migrationDisabled)}
                  title={intl.formatMessage({ id: 'admin.card.reset.title', defaultMessage: 'Reset' })}
                  description={
                    <>
                      <FormattedMessage
                        id='admin.card.reset.description'
                        defaultMessage='Clears migration progress and re-enables UI'
                      />
                      <br />
                      <b>
                        <FormattedMessage
                          id='admin.card.reset.stuck'
                          defaultMessage='Only use when the migration is stuck'
                        />
                      </b>
                    </>
                  }
                  icon={faCode}
                  onClick={() => handleClick('migrate-reset')}
                />
              </Grid.Col>
          </Grid>
          <Grid>
            <Grid.Col span={12}>
              <Stack>
                <Title order={4}><FormattedMessage id='admin.tools.title' defaultMessage='Tools' /></Title>
                <Text c='dimmed'>
                  <FormattedMessage
                    id='admin.tools.description'
                    defaultMessage='Please use the following tools <b>with caution</b> and <b>verify</b> that these really need to be run first.'
                    values={{ b: (chunks) => <b>{chunks}</b> }}
                  />
                </Text>
              </Stack>
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                disabled={Boolean(migrationProgress) || migrationDisabled}
                title={intl.formatMessage({ id: 'admin.card.rematch.title', defaultMessage: 'Rematch' })}
                description={intl.formatMessage({ id: 'admin.card.rematch.description', defaultMessage: 'Rematch the taxonomy for all lists' })}
                icon={faIdCard}
                onClick={() => handleClick('rematch')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                disabled={Boolean(migrationProgress) || migrationDisabled}
                title={intl.formatMessage({ id: 'admin.card.reindex.title', defaultMessage: 'Reindex' })}
                description={intl.formatMessage({ id: 'admin.card.reindex.description', defaultMessage: 'Regenerate the elastic search index for all lists' })}
                icon={faArrowsRotate}
                onClick={() => handleClick('reindex')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
              <ActionCard
                title={intl.formatMessage({ id: 'admin.card.mongoIndexes.title', defaultMessage: 'Mongo indexes' })}
                description={intl.formatMessage({ id: 'admin.card.mongoIndexes.description', defaultMessage: 'View indexed fields for MongoDB collections' })}
                icon={faSearch}
                onClick={() => {
                  modals.open({
                    size: 'xl',
                    title: (
                      <Text fw='bold' size='lg'>
                        <FormattedMessage
                          id='admin.mongoIndexes.modal.title'
                          defaultMessage='Mongo Indexes'
                        />
                      </Text>
                    ),
                    children: <FetchInfo fetcher={ala.rest.admin?.indexes} />,
                  });
                }}
              />
            </Grid.Col>
          </Grid>
          {/* {['testing', 'development'].includes(import.meta.env.MODE) && ( */}
            <Grid>
              <Grid.Col span={12}>
                <Stack>
                  <Title order={4}><FormattedMessage id='admin.danger.title' defaultMessage='Danger Zone' /></Title>
                  <Alert
                    icon={<FontAwesomeIcon icon={faWarning} color='white' />}
                    title={intl.formatMessage({ id: 'admin.danger.title', defaultMessage: 'Danger Zone' })}
                    variant='filled'
                    radius='md'
                  >
                    <FormattedMessage
                      id='admin.danger.description'
                      defaultMessage='Extremely massive danger zone here. <b>Run these ONLY in development/testing environments.</b>'
                      values={{ b: (chunks) => <b>{chunks}</b> }}
                    />
                  </Alert>
                </Stack>
              </Grid.Col>
              <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
                <ActionCard
                  disabled={Boolean(migrationProgress) || migrationDisabled}
                  title={intl.formatMessage({ id: 'admin.card.deleteIndex.title', defaultMessage: 'Delete index' })}
                  description={intl.formatMessage({ id: 'admin.card.deleteIndex.description', defaultMessage: 'Clear all data from the Elasticsearch index (requires reboot after running)' })}
                  icon={faTrash}
                  onClick={() => handleClick('wipe-index')}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
                <ActionCard
                  disabled={Boolean(migrationProgress) || migrationDisabled}
                  title={intl.formatMessage({ id: 'admin.card.deleteDocs.title', defaultMessage: 'Delete docs' })}
                  description={intl.formatMessage({ id: 'admin.card.deleteDocs.description', defaultMessage: 'Clear all data from the MongoDB collections (requires reimport)' })}
                  icon={faTrash}
                  onClick={() => handleClick('wipe-docs')}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, xs: 6, sm: 4, md: 4 }}>
                <ActionCard
                  disabled={Boolean(migrationProgress) || migrationDisabled}
                  title={intl.formatMessage({ id: 'admin.card.reboot.title', defaultMessage: 'Reboot' })}
                  description={intl.formatMessage({ id: 'admin.card.reboot.description', defaultMessage: 'Reboot the lists application' })}
                  icon={faRefresh}
                  onClick={() => handleClick('reboot')}
                />
              </Grid.Col>
            </Grid>
          {/* )} */}
        </Stack>
      </Container>
    </>
  );
}

Object.assign(Component, { displayName: 'Admin' });

export default Component;
