/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Container,
  Group,
  Paper,
  Stack,
  Text,
} from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { useParams } from 'react-router';
import { FormattedMessage, useIntl } from 'react-intl';

// Helpers & local components
import { performGQLQuery, queries, SpeciesList, UploadResult } from '#/api';
import { getErrorMessage } from '#/helpers';
import { IngestProgress } from '#/components/IngestProgress';
import { useALA } from '#/helpers/context/useALA';
import { getAccessToken } from '#/helpers/utils/getAccessToken';
import { FileUploadDropzone } from '#/components/FileUploadDropzone';

/**
 * Reingest component for reingesting species lists
 * 
 * @returns {JSX.Element} - The rendered Reingest component
 */
export default function Reingest() {
  const { id } = useParams();
  const [ingesting, setIngesting] = useState<boolean>(false);
  const [error, setError] = useState<string | Error | null>(null);
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

  // Handler for successful file upload
  const handleUploadSuccess = useCallback((uploadResult: UploadResult) => {
    setResult(uploadResult);
  }, []);

  // Callback handler for ingestion
  const handleIngest = useCallback(async () => {
    setIngesting(true);
    try {
      await ala.rest.lists.reingest(meta?.id || '', result?.localFile || '');
    } catch (error) {
      setIngesting(false);
      notifications.show({
        message: getErrorMessage(error),
        position: 'bottom-left',
        radius: 'md',
      });
    }
  }, [result?.localFile, meta?.id]);

  const handleReset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  return (
    <Container size="lg">
      <FileUploadDropzone
        onUploadSuccess={handleUploadSuccess} 
        onReset={handleReset}
        uploadType='reingest'
        showHelpText={true}
        initialTitle={intl.formatMessage({ id: 'reingest.upload.description', defaultMessage: 'Drag list here, or click to select' })}
      />
      
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
    </Container>
  );
}