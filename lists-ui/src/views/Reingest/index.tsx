/* eslint-disable react-hooks/exhaustive-deps */
import '@mantine/dropzone/styles.css';

import { ReactNode, useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Flex,
  Group,
  Paper,
  Stack,
  Text,
} from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { Dropzone, FileWithPath } from '@mantine/dropzone';
import {
  ArrowUpIcon,
  CautionIcon,
  FolderIcon,
  StopIcon,
  TickIcon,
} from '@atlasoflivingaustralia/ala-mantine';
import { notifications } from '@mantine/notifications';
import { useParams } from 'react-router';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';

// Helpers & local components
import { performGQLQuery, queries, SpeciesList, UploadResult } from '#/api';
import { getErrorMessage } from '#/helpers';
import { IngestProgress } from '#/components/IngestProgress';
import { useALA } from '#/helpers/context/useALA';
import { getAccessToken } from '#/helpers/utils/getAccessToken';
import classes from './index.module.css';

const ACCEPTED_TYPES: string[] = ['text/csv', 'application/zip'];

export default function Reingest() {

  const { id } = useParams();
  const [uploading, setUploading] = useState<boolean>(false);
  const [ingesting, setIngesting] = useState<boolean>(false);
  const [error, setError] = useState<string | Error | null>(null);
  const [originalName, setOriginalName] = useState<string | null>(null);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [meta, setMeta] = useState<SpeciesList | null>(null);
  const [_loading, setLoading] = useState(true);

  useDocumentTitle(meta?.title + ' reingest' || 'Loading...');
  const ala = useALA();
  const intl = useIntl();

  useEffect(() => {
    const fetchData = async () => {
      try {
        const token = getAccessToken();
        const result = await performGQLQuery(
          queries.QUERY_LISTS_GET,
          {
            speciesListID: id,
          },
          token
        );

        if (result.meta === null || result.list === null) {
          throw new Error(intl.formatMessage({ id: 'reingest.error.list.notFount', defaultMessage: 'List not found' }));
        }

        setMeta(result.meta);
      } catch (err) {
        setError(err as Error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id]);

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
  const handleIngest = useCallback(async () => {
    setIngesting(true);
    try {
      await ala.rest.lists.reingest(meta?.id || '', result?.localFile || '');
    } catch (error) {
      // console.error(error);
      notifications.show({
        message: getErrorMessage(error),
        position: 'bottom-left',
        radius: 'md',
      });
    }
  }, [result?.localFile]);

  const handleReset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  let idle: { title: ReactNode; content: ReactNode; icon: ReactNode } = {
    title: intl.formatMessage({ id: 'reingest.upload.list.desription', defaultMessage: 'Drag list here, or click to select' }),
    content: (
      <>
        <Text size='sm' c='dimmed' inline>
          <FormattedMessage id='reingest.upload.list.acceptedFiles' defaultMessage='Accepted files'/>
        </Text>
        <Badge>CSV</Badge>
        <Badge>ZIP</Badge>
      </>
    ),
    icon: <ArrowUpIcon size={40} />,
  };

  if (error) {
    idle = {
      title: intl.formatMessage({ id: 'reingest.error.message.title', defaultMessage: 'An error has occurred' }),
      content: (
        <Text size='sm' c='dimmed'>
          {getErrorMessage(error)}, <FormattedMessage id='reingest.error.message.tryAgain' defaultMessage='please try again with another file'/>
        </Text>
      ),
      icon: <CautionIcon size={40} />,
    };
  } else if (result) {
    idle = {
      title: `${intl.formatMessage({ id: 'reingest.success.message.prefix', defaultMessage: 'Uploaded' })} ${originalName}`,
      content: (
        <>
          <Badge> {<FormattedNumber value={result.rowCount} />} rows</Badge>
          <Text lineClamp={2} size='sm' c='dimmed'>
            <b><FormattedMessage id='reingest.additional.files' defaultMessage='Additional Fields'/>:</b>{' '}
            {result.fieldList.length > 0 ? result.fieldList.join(', ') : 'N/A'}
          </Text>
        </>
      ),
      icon: <FolderIcon size={40} />,
    };
  }

  const uploadDisabled = Boolean(result);

  return (
    <Stack>
      <Dropzone
        onDrop={handleUpload}
        accept={ACCEPTED_TYPES}
        radius='lg'
        loading={uploading}
        disabled={uploadDisabled}
        maxSize={52428800}
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
              <FormattedMessage id='reingest.upload.tryAgain' defaultMessage='Try again'/>
            </Button>
          </div>
        </Alert>
      </div>
      {result && !result.validationErrors && !ingesting && (
        <Paper p='md' radius='lg' withBorder>
          <Group justify='space-between'>
            <Stack gap={4}>
              <Text size='lg' fw='bold'>
                <FormattedMessage id='reingest.upload.complete' defaultMessage='Upload complete'/>
              </Text>
              <Text size='sm' c='dimmed'>
                <FormattedMessage id='reingest.new.list.for' defaultMessage='New list for'/>{' '}
                <b>{meta?.title ?? <FormattedMessage id='reingest.unknown' defaultMessage='Unknown'/>}</b> {' '}
                <FormattedMessage id='reingest.uploaded' defaultMessage='uploaded'/>
              </Text>
            </Stack>
            <Group>
              <Button onClick={handleIngest}><FormattedMessage id='reingest.confirm.reingestion' defaultMessage='Confirm re-ingestion'/></Button>
              <Button onClick={handleReset}><FormattedMessage id='reingest.cacel.reingestion' defaultMessage='Cancel'/></Button>
            </Group>
          </Group>
        </Paper>
      )}
      <IngestProgress id={id ?? null} ingesting={ingesting} />
    </Stack>
  );
}
