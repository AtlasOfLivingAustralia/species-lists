import {
  Badge,
  Chip,
  Divider,
  Group,
  GroupProps,
  Text,
  Tooltip,
} from '@mantine/core';
import { FormattedMessage } from 'react-intl';

// Icons
import { faCreativeCommons } from '@fortawesome/free-brands-svg-icons';
import {
  faBank,
  faGlobe,
  faIdBadge,
  faUser,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

// API
import { SpeciesList } from '#/api';

interface SummaryProps extends GroupProps {
  meta: SpeciesList;
}

export function Summary({ meta, ...rest }: SummaryProps) {
  return (
    <Group {...rest} gap='xs' align='center'>
      <Text>
        <FormattedMessage
          id={meta.listType ? meta.listType : 'summary.listtype.notFound'}
        />
      </Text>
      <Divider mx={4} orientation='vertical' />
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
        <Tooltip position='right' label='View in Collectory' withArrow>
          <Badge
            h={23}
            variant='light'
            color='gray'
            leftSection={<FontAwesomeIcon icon={faIdBadge} fontSize={10} />}
            component='a'
            target='_blank'
            href={`${import.meta.env.VITE_ALA_COLLECTORY}/${
              meta.isPrivate ? 'dataResource' : 'public'
            }/show/${meta.dataResourceUid}`}
            style={{ cursor: 'pointer' }}
          >
            <span
              style={{
                textTransform: 'lowercase',
                color: 'var(--chip-color)',
                fontSize: 'var(--mantine-font-size-xs)',
                fontWeight: 400,
              }}
            >
              {meta.dataResourceUid}
            </span>
          </Badge>
        </Tooltip>
      )}
    </Group>
  );
}
