/* eslint-disable react-hooks/exhaustive-deps */
import { useState, useCallback } from 'react';
import {
  Container,
  Grid,
  Stack,
  Title,
} from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { FormattedMessage, useIntl } from 'react-intl';
import { SpeciesListSubmit, UploadResult } from '#/api';
import { notifications } from '@mantine/notifications';
import { getErrorMessage } from '#/helpers';

// Helpers & local components
import { ListMeta } from '#/components/ListMeta';
import { useALA } from '#/helpers/context/useALA';
import { IngestProgress } from '#/components/IngestProgress';
import { Breadcrumbs } from '../Dashboard/components/Breadcrumbs';
import { FileUploadDropzone } from '#/components/FileUploadDropzone';

import classes from './index.module.css';

/**
 * Upload component for uploading species lists
 * 
 * @returns {JSX.Element} - The rendered Upload component
 */
export default function Component() {
  useDocumentTitle('ALA Lists | Upload');

  const [ingesting, setIngesting] = useState<boolean>(false);
  const [originalName, setOriginalName] = useState<string | null>(null);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [ingestId, setIngestId] = useState<string | null>(null);

  const ala = useALA();
  const intl = useIntl();

  // Handler for successful file upload
  const handleUploadSuccess = useCallback((uploadResult: UploadResult, fileName: string) => {
    setOriginalName(fileName);
    setResult(uploadResult);
  }, []);

  // Callback handler for ingestion
  const handleIngest = useCallback(
    async (list: SpeciesListSubmit) => {
      setIngesting(true);
      try {
        const { id } = await ala.rest.lists.ingest(
          list,
          result?.localFile || ''
        );
        setIngestId(id);
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
    setResult(null);
    setIngestId(null);
  }, []);

  return (
    <>
      <Container fluid className={classes.speciesHeader}>
        <Grid>
          <Grid.Col span={12}>
            <Breadcrumbs listTitle={intl.formatMessage({ id: 'upload.title', defaultMessage: 'Upload' })}/>
          </Grid.Col>
          <Grid.Col span={12}>
            <Title order={3} classNames={{ root: classes.title }} >
              <FormattedMessage id='upload.title.label' defaultMessage='Upload a list' />
            </Title>
          </Grid.Col>
        </Grid>
      </Container>
      <Container size="lg">
        <Stack mt='xl'>
          <FileUploadDropzone
            onUploadSuccess={handleUploadSuccess}
            onReset={handleReset}
            uploadType='upload'
            showHelpText={true}
          />
          
          {result && !result.validationErrors && (
            <ListMeta
              ala={ala}
              loading={ingesting}
              onReset={handleReset}
              onSubmit={handleIngest}
              initialTitle={originalName || undefined}
            />
          )}
          <IngestProgress id={ingestId} ingesting={ingesting} />
        </Stack>
      </Container>
    </>
  );
}

Object.assign(Component, { displayName: 'Upload' });