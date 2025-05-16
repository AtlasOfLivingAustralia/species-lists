/* eslint-disable react-hooks/exhaustive-deps */
import '@mantine/dropzone/styles.css';

import { ReactNode, useCallback, useState } from 'react';
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Code,
  Flex,
  Group,
  Modal,
  Paper,
  Space,
  Stack,
  Text,
} from '@mantine/core';
import { Dropzone, FileWithPath } from '@mantine/dropzone';
import {
  ArrowUpIcon,
  CautionIcon,
  FolderIcon,
  StopIcon,
  TickIcon,
} from '@atlasoflivingaustralia/ala-mantine';
import { FormattedMessage, FormattedNumber, useIntl } from 'react-intl';
import { UploadResult } from '#/api';
import { getErrorMessage } from '#/helpers';
import { useALA } from '#/helpers/context/useALA';

// Import component styles
import classes from './FileUploadDropzone.module.css';
import { useDisclosure } from '@mantine/hooks';

// Accepted file types
const ACCEPTED_TYPES: string[] = ['text/csv', 'application/zip'];

type uploadTypes = 'upload' | 'reingest';

export interface FileUploadDropzoneProps {
  onUploadSuccess?: (result: UploadResult, originalName: string) => void;
  onReset?: () => void;
  uploadType: uploadTypes;
  showHelpText?: boolean;
  initialTitle?: string;
}

/**
 * Reusable file upload component for species lists
 * 
 * @param {FileUploadDropzoneProps} props - Component props
 * @returns {JSX.Element} - The rendered FileUploadDropzone component
 */
export const FileUploadDropzone = ({
  onUploadSuccess,
  onReset,
  uploadType = 'upload',
  showHelpText = false,
  initialTitle,
}: FileUploadDropzoneProps) => {
  const [uploading, setUploading] = useState<boolean>(false);
  const [error, setError] = useState<string | Error | null>(null);
  const [originalName, setOriginalName] = useState<string | null>(initialTitle || null);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [opened, { open, close }] = useDisclosure(false);

  const ala = useALA();
  const intl = useIntl();

  // Callback handler for upload
  const handleUpload = useCallback(async (files: FileWithPath[]) => {
    try {
      setError(null);
      setUploading(true);
      setOriginalName(files[0].name);

      const uploadResult = await ala.rest.lists.upload(files);
      setResult(uploadResult);
      
      if (onUploadSuccess && !uploadResult.validationErrors) {
        onUploadSuccess(uploadResult, files[0].name);
      }
    } catch (error) {
      setError(getErrorMessage(error as string || error));
    }

    setUploading(false);
  }, [onUploadSuccess]);

  const handleReset = useCallback(() => {
    setError(null);
    setResult(null);
    if (onReset) {
      onReset();
    }
  }, [onReset]);

  let idle: { title: ReactNode; content: ReactNode; icon: ReactNode } = {
    title: intl.formatMessage({ id: 'upload.list.description', defaultMessage: 'Drag list here, or click to select' }),
    content: (
      <>
        <Text size='sm' c='dimmed' inline>
          <FormattedMessage id='upload.list.acceptedFiles' defaultMessage='Accepted files' />
        </Text>
        <Badge>CSV</Badge>
        <Badge>ZIP</Badge>
      </>
    ),
    icon: <ArrowUpIcon size={40} />,
  };

  if (error) {
    idle = {
      title: intl.formatMessage({ id: 'upload.error.message.title', defaultMessage: 'An error has occurred' }),
      content: (
        <Text size='sm' c='dimmed'>
          {getErrorMessage(error)}, <FormattedMessage id='upload.error.message.tryAgain' defaultMessage='please try again with another file' />
        </Text>
      ),
      icon: <CautionIcon size={40} />,
    };
  } else if (result) {
    idle = {
      title: `${intl.formatMessage({ id: 'upload.success.message.prefix', defaultMessage: 'Uploaded' })} ${originalName}`,
      content: (
        <>
          <Badge> {<FormattedNumber value={result.rowCount} />} rows</Badge>
          <Text lineClamp={2} size='sm' c='dimmed'>
            <b><FormattedMessage id='upload.additional.fields' defaultMessage='Additional Fields' />:</b>{' '}
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
      {showHelpText && (
        <Group justify="center">
          <Paper bg="var(--mantine-color-blue-light)" w="100%" shadow="xs" radius="lg" p="xl" mt={20}>
            <strong>
              { uploadType === 'upload' ? ( 
                <FormattedMessage
                  id='upload.help.prefix'
                  defaultMessage='Upload'
                  />
              ) : (
                <FormattedMessage
                  id='reingest.help.prefix'
                  defaultMessage='Reingest'
                />
              )} {' '}
              <FormattedMessage
                id='upload.help.title'
                defaultMessage='a species list in comma-separated values (CSV) format'
              />
            </strong>
            <Space h="md" />
            <FormattedMessage
              id='upload.help.description'
              defaultMessage='List upload formatting mandatory requirements'
              values={{ 
                ul: chunks => <ul>{chunks}</ul>,
                li: chunks => <li>{chunks}</li>,
                csv: chunks => <Anchor href="https://en.wikipedia.org/wiki/Comma-separated_values#Specification" target="_blank">{chunks}</Anchor>,
                code: chunks => <Code className={classes.code}>{chunks}</Code>,
                dwc: chunks => <Anchor href="https://dwc.tdwg.org/terms/" target="_blank">{chunks}</Anchor> }}
            />
            <Modal opened={opened} onClose={close} size="auto" title={<FormattedMessage id='upload.help.example.title' defaultMessage='Example CSV file' />}>
              <Code block className={classes.codeBlock}>
                <FormattedMessage
                  id='upload.help.csv.content'
                  defaultMessage='scienficificName, commonName, state, country, size, weight,'
                  values={{ 
                    br: <br />,
                  }}
                />
              </Code>
            </Modal>
            <Button variant="default" onClick={open}>
              <FormattedMessage id='upload.help.example.title' defaultMessage='Example CSV file' />
            </Button>

          </Paper>
        </Group>
      )}
      
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
              <FormattedMessage id='upload.tryAgain' defaultMessage='Try again' />
            </Button>
          </div>
        </Alert>
      </div>
    </Stack>
  );
};