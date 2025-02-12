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
import { IngestProgress as IngestProgressType } from '#/api';

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
  disableNavigation?: boolean;
  onProgress?: (progress: IngestProgressType) => void;
}

export function IngestProgress({
  id,
  ingesting,
  disableNavigation = false,
  onProgress,
}: IngestProgressProps) {
  const [update, setUpdate] = useState<boolean>(false);
  const [progress, setProgress] = useState<IngestProgressType>({
    id: '',
    speciesListId: '',
    rowCount: 0,
    elasticTotal: 0,
    mongoTotal: 0,
    started: 0,
  });

  const ala = useALA();
  const isMobile = useMediaQuery(`(max-width: ${em(750)})`);
  const navigate = useNavigate();

  // Calculate ingest step
  let step = 0;
  if (ingesting) {
    if (progress.mongoTotal > 0 && progress.mongoTotal < progress.rowCount) {
      step = 1;
    } else if (progress.mongoTotal === progress.rowCount) {
      step = 2;
    }
  }

  // Start polling for ingest progress when the ingest flag is true
  useEffect(() => {
    async function getStatus() {
      if (id && ingesting) {
        const progress = await ala.rest.lists.ingestProgress(id);

        setProgress(progress);
        if (onProgress) onProgress(progress);

        // If the list has been ingested successfully, navigate to it, otherwise, wait a bit and check again
        if (
          progress.elasticTotal === progress.rowCount &&
          progress.rowCount > 0 &&
          !disableNavigation
        ) {
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
            progress.rowCount > 10000 ? 3000 : 1500
          );
        }
      }
    }
    if (id && ingesting) getStatus();
  }, [id, ingesting, update]);

  return (
    <Box
      mt='sm'
      h={ingesting ? 110 : 0}
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
        <Progress.Root h={20} mt='md' radius='md'>
          <Progress.Section
            animated={step !== 1}
            value={
              step === 0
                ? 100
                : (progress.mongoTotal / progress.rowCount) * 80 +
                  (progress.elasticTotal / progress.rowCount) * 20
            }
          >
            {step === 1 && (
              <Text
                c='white'
                size='xs'
                style={{ overflow: 'hidden', textWrap: 'nowrap' }}
              >
                <b>
                  {Math.floor((progress.mongoTotal / progress.rowCount) * 100)}%
                </b>
                <span style={{ marginLeft: 8, opacity: 0.7 }}>
                  {progress.mongoTotal}/{progress.rowCount}
                </span>
              </Text>
            )}
          </Progress.Section>
        </Progress.Root>
      </Paper>
    </Box>
  );
}
