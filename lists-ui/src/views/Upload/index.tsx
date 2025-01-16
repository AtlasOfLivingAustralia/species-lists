/* eslint-disable react-hooks/exhaustive-deps */
import '@mantine/dropzone/styles.css';

import { ReactNode, useCallback, useState } from 'react';
import {
  Alert,
  Badge,
  Box,
  Button,
  Container,
  em,
  Flex,
  Group,
  Paper,
  Progress,
  Stack,
  Stepper,
  Text,
} from '@mantine/core';
import { useDocumentTitle, useMediaQuery } from '@mantine/hooks';
import { Dropzone, FileWithPath } from '@mantine/dropzone';
import {
  ArrowUpIcon,
  CautionIcon,
  FolderIcon,
  StopIcon,
  TickIcon,
} from '@atlasoflivingaustralia/ala-mantine';

import { FormattedMessage, FormattedNumber } from 'react-intl';
import { SpeciesListSubmit, UploadResult } from '#/api';
import { getErrorMessage } from '#/helpers';

// Helpers & local components
import { ListMeta } from '#/components/ListMeta';
import { notifications } from '@mantine/notifications';
import classes from './index.module.css';
import { useALA } from '#/helpers/context/useALA';
import { Navigate, useNavigate } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faBoxOpen,
  faCheck,
  faList,
  faSearch,
} from '@fortawesome/free-solid-svg-icons';

const ACCEPTED_TYPES: string[] = ['text/csv', 'application/zip'];

// Content / icon definitions for ingestion steps
const steps = [
  {
    text: 'Setting up list',
    icon: faBoxOpen,
  },
  {
    text: 'Matching taxa',
    icon: faSearch,
  },
  {
    text: 'Finalizing list',
    icon: faList,
  },
];

export function Component() {
  useDocumentTitle('ALA Lists | Upload');

  const [uploading, setUploading] = useState<boolean>(false);
  const [ingesting, setIngesting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [originalName, setOriginalName] = useState<string | null>(null);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [progress, setProgress] = useState<{ elastic: number; mongo: number }>({
    elastic: 0,
    mongo: 0,
  });

  const isMobile = useMediaQuery(`(max-width: ${em(750)})`);
  const navigate = useNavigate();
  const ala = useALA();

  // Callback handler for upload
  const handleUpload = useCallback(async (files: FileWithPath[]) => {
    try {
      setError(null);
      setUploading(true);
      setOriginalName(files[0].name);

      const uploadResult = await ala.rest.lists.upload(files);
      setResult(uploadResult);
    } catch (error) {
      setError(getErrorMessage(error as string));
    }

    setUploading(false);
  }, []);

  // Callback handler for ingestion
  const handleIngest = useCallback(
    async (list: SpeciesListSubmit) => {
      setIngesting(true);
      try {
        const { id } = await ala.rest.lists.ingest(
          list,
          result?.localFile || '',
          true
        );

        const status = async () => {
          const progress = await ala.rest.lists.ingestProgress(id);

          setProgress(progress);

          // If the list has been ingested successfully, navigate to it, otherwise, wait a bit and check again
          if (result?.rowCount === progress.elastic) {
            setTimeout(() => navigate(`/list/${id}`), 500);
          } else {
            setTimeout(() => status(), 1500);
          }
        };
        status();
      } catch (error) {
        setIngesting(false);

        notifications.show({
          message: getErrorMessage(error),
          position: 'bottom-left',
          radius: 'md',
        });
      }
    },
    [result?.localFile]
  );

  const handleReset = useCallback(() => {
    setError(null);
    setResult(null);
    setProgress({ mongo: 0, elastic: 0 });
  }, []);

  // Redirect to the home screen if not authenticated
  if (!ala.isAuthenticated) return <Navigate to='/' />;

  let idle: { title: ReactNode; content: ReactNode; icon: ReactNode } = {
    title: 'Drag list here, or click to select',
    content: (
      <>
        <Text size='sm' c='dimmed' inline>
          Accepted files
        </Text>
        <Badge>CSV</Badge>
        <Badge>ZIP</Badge>
      </>
    ),
    icon: <ArrowUpIcon size={40} />,
  };

  if (error) {
    idle = {
      title: 'An error has occurred',
      content: (
        <Text size='sm' c='dimmed'>
          {error}, please try again with another file
        </Text>
      ),
      icon: <CautionIcon size={40} />,
    };
  } else if (result) {
    idle = {
      title: `Uploaded ${originalName}`,
      content: (
        <>
          <Badge> {<FormattedNumber value={result.rowCount} />} rows</Badge>
          <Text lineClamp={2} size='sm' c='dimmed'>
            <b>Additional Fields: </b>{' '}
            {result.fieldList.length > 0 ? result.fieldList.join(', ') : 'N/A'}
          </Text>
        </>
      ),
      icon: <FolderIcon size={40} />,
    };
  }

  const uploadDisabled = Boolean(result);

  // Calculate ingest step
  let step = 0;
  if (ingesting) {
    if (progress.mongo > 0 && progress.mongo < (result?.rowCount || 0)) {
      step = 1;
    } else if (progress.mongo === (result?.rowCount || 0)) {
      step = 2;
    }
  }

  return (
    <Container size='lg'>
      <Stack>
        <Dropzone
          onDrop={handleUpload}
          accept={ACCEPTED_TYPES}
          radius='lg'
          loading={uploading}
          disabled={uploadDisabled}
          maxSize={26214400}
          className={uploadDisabled ? classes.disabled : undefined}
          multiple={false}
        >
          <Flex
            className={classes.inner}
            justify='center'
            align='center'
            gap='lg'
            style={{ height: result ? 100 : 180 }}
          >
            <Dropzone.Accept>
              <TickIcon size={40} />
            </Dropzone.Accept>
            <Dropzone.Reject>
              <StopIcon size={40} />
            </Dropzone.Reject>
            <Dropzone.Idle>{idle.icon}</Dropzone.Idle>

            <div style={{ overflow: 'hidden' }}>
              <Text size='xl' truncate='end' inline>
                {idle.title}
              </Text>
              <Group gap='xs' align='flex-end' mt={7}>
                {idle.content}
              </Group>
            </div>
          </Flex>
        </Dropzone>
        <div
          style={{
            overflow: 'hidden',
            transition: 'all ease 200ms',
            height: result?.validationErrors ? 100 : 0,
          }}
        >
          <Alert icon={<CautionIcon />} radius='lg'>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              {(result?.validationErrors || []).map((message) => (
                <FormattedMessage key={message} id={message} />
              ))}
              <Button
                size='xs'
                miw={80}
                ml='sm'
                variant='outline'
                color='dark'
                onClick={() => setResult(null)}
              >
                Try again
              </Button>
            </div>
          </Alert>
        </div>
        {result && !result.validationErrors && (
          <ListMeta
            ala={ala}
            loading={ingesting}
            onReset={handleReset}
            onSubmit={handleIngest}
          />
        )}
        <Box
          mt='sm'
          h={ingesting ? 92 : 0}
          style={{
            overflow: 'hidden',
            transition: 'height 250ms ease-in-out',
          }}
        >
          <Paper p='sm' radius='lg' withBorder>
            {isMobile ? (
              <Group justify='center'>
                <FontAwesomeIcon icon={steps[step].icon} />
                <Text fw='bold' size='lg'>
                  {steps[step].text}
                </Text>
              </Group>
            ) : (
              <Stepper
                active={step}
                allowNextStepsSelect={false}
                completedIcon={<FontAwesomeIcon icon={faCheck} />}
              >
                {steps.map((step) => (
                  <Stepper.Step
                    icon={<FontAwesomeIcon icon={step.icon} />}
                    label={step.text}
                  />
                ))}
              </Stepper>
            )}
            <Progress
              mt='md'
              animated={step !== 1}
              value={
                step === 0
                  ? 100
                  : (progress.mongo / (result?.rowCount || 0)) * 100
              }
            />
          </Paper>
        </Box>
      </Stack>
    </Container>
  );
}

Object.assign(Component, { displayName: 'Upload' });
