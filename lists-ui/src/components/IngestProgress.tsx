import { useEffect, useState } from 'react';
import { Box, em, Group, Paper, Progress, Stepper, Text } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useNavigate } from 'react-router';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faBoxOpen,
  faCheck,
  faList,
  faSearch,
} from '@fortawesome/free-solid-svg-icons';
import { useALA } from '#/helpers/context/useALA';
import { IngestProgress as IngestProgressType, UploadResult } from '#/api';

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

interface IngestProgressProps {
  id: string | null;
  ingesting: boolean;
  result: UploadResult | null;
  disableNavigation?: boolean;
  onProgress?: (progress: IngestProgressType) => void;
}

export function IngestProgress({
  id,
  ingesting,
  result,
  disableNavigation = false,
  onProgress,
}: IngestProgressProps) {
  const [update, setUpdate] = useState<boolean>(false);
  const [progress, setProgress] = useState<IngestProgressType>({
    elastic: 0,
    mongo: 0,
  });

  const ala = useALA();
  const isMobile = useMediaQuery(`(max-width: ${em(750)})`);
  const navigate = useNavigate();

  // Calculate ingest step
  let step = 0;
  if (ingesting) {
    if (progress.mongo > 0 && progress.mongo < (result?.rowCount || 0)) {
      step = 1;
    } else if (progress.mongo === (result?.rowCount || 0)) {
      step = 2;
    }
  }

  // Start polling for ingest progress when the ingest flag is true
  useEffect(() => {
    async function getStatus() {
      if (id && ingesting && result) {
        const progress = await ala.rest.lists.ingestProgress(id);

        setProgress(progress);
        if (onProgress) onProgress(progress);

        // If the list has been ingested successfully, navigate to it, otherwise, wait a bit and check again
        if (result?.rowCount === progress.elastic && !disableNavigation) {
          setTimeout(
            () =>
              navigate(`/list/${id}`, {
                replace: true,
              }),
            500
          );
        } else {
          setTimeout(
            () => setUpdate(!update),
            (result?.rowCount || 0) > 10000 ? 3000 : 1500
          );
        }
      }
    }
    if (id && ingesting && result) getStatus();
  }, [id, ingesting, result, update]);

  return (
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
            {steps.map((stepInfo) => (
              <Stepper.Step
                key={stepInfo.text}
                icon={<FontAwesomeIcon icon={stepInfo.icon} />}
                label={stepInfo.text}
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
              : (progress.mongo / (result?.rowCount || 0)) * 80 +
                (progress.elastic / (result?.rowCount || 0)) * 20
          }
        />
      </Paper>
    </Box>
  );
}
