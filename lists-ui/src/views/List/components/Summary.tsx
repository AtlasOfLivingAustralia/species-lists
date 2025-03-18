import {
  Chip,
  Divider,
  Group,
  GroupProps,
  Text,
  Tooltip,
  UnstyledButton,
} from '@mantine/core';
import { FormattedMessage } from 'react-intl';

// Icons
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCreativeCommons } from '@fortawesome/free-brands-svg-icons';
import {
  faBank,
  faGlobe,
  faIdBadge,
  faUser,
} from '@fortawesome/free-solid-svg-icons';

// API
import { SpeciesList } from '#/api';
import { useClipboard } from '@mantine/hooks';

interface SummaryProps extends GroupProps {
  meta: SpeciesList;
}

export function Summary({ meta, ...rest }: SummaryProps) {
  const clipboard = useClipboard();

  return (
    <Group {...rest} gap='xs' align='center'>
      {meta.listType && (
        <>
          <Text>
            <FormattedMessage id={meta.listType} />
          </Text>
          <Divider mx={4} orientation='vertical' />
        </>
      )}
      {meta.region && (
        <Chip
          size='xs'
          variant='light'
          color='gray'
          checked={true}
          icon={<FontAwesomeIcon icon={faGlobe} fontSize={10} />}
        >
          <FormattedMessage
            id={`region.${meta.region || ''}`}
            defaultMessage={meta.region}
          />
        </Chip>
      )}
      {meta.ownerName && (
        <Chip
          size='xs'
          variant='light'
          color='gray'
          checked={true}
          icon={<FontAwesomeIcon icon={faUser} fontSize={10} />}
        >
          {meta.ownerName}
        </Chip>
      )}
      {meta.authority && (
        <Chip
          size='xs'
          variant='light'
          color='gray'
          checked={true}
          icon={<FontAwesomeIcon icon={faBank} fontSize={10} />}
        >
          {meta.authority || 'Authority'}
        </Chip>
      )}
      <Chip
        size='xs'
        variant='light'
        color='gray'
        checked={true}
        icon={<FontAwesomeIcon icon={faCreativeCommons} fontSize={10} />}
      >
        {meta.licence ? (
          <FormattedMessage
            id={`licence.${meta.licence}`}
            defaultMessage={meta.licence}
          />
        ) : (
          'No licence'
        )}
      </Chip>
      {meta.dataResourceUid && (
        <Tooltip
          position='right'
          label={clipboard.copied ? 'Copied' : 'Click to copy'}
          withArrow
        >
          <UnstyledButton
            component='a'
            target='_blank'
            onClick={() => clipboard.copy(meta.dataResourceUid)}
          >
            <Chip
              size='xs'
              variant='light'
              color='gray'
              checked={true}
              icon={<FontAwesomeIcon icon={faIdBadge} fontSize={10} />}
            >
              {meta.dataResourceUid || 'DR'}
            </Chip>
          </UnstyledButton>
        </Tooltip>
      )}
    </Group>
  );
}
