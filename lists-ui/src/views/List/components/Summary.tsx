import {
  Badge,
  Group,
  GroupProps,
  Tooltip
} from '@mantine/core';
import { FormattedMessage, useIntl } from 'react-intl';

// Icons
import { faCreativeCommons } from '@fortawesome/free-brands-svg-icons';
import {
  faBank,
  faBookmark,
  faGlobe,
  faIdBadge,
  faTag,
  faUser,
  IconDefinition
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

// API
import { SpeciesList } from '#/api';
import classes from '../classes/Summary.module.css';

interface SummaryProps extends GroupProps {
  meta: SpeciesList;
}

function MetaBadge({
  typeName,
  typeValue,
  href,
  color = 'default',
  icon,
  children,
}: {
  typeName?: string;
  typeValue: string;
  href?: string;
  color?: string;
  icon: IconDefinition;
  children?: React.ReactNode;
}) {
  const intl = useIntl();
  return (
    <Tooltip 
        position='bottom' 
        label={intl.formatMessage({ id: 'summary.' + typeName + '.tooltip', defaultMessage: typeName })} 
        withArrow
    >
      <span>
        <Badge
          variant='light'
          color={color || undefined}
          leftSection={<FontAwesomeIcon icon={icon} fontSize={10} />}
          className={classes.metaBadge}
          style={!color ? { fontWeight: 500 } : href ? { cursor: 'pointer' } : {}}
          component={href ? 'a' : undefined}
          {...(href ? { href } : {})}
        >
          <FormattedMessage id={typeValue || '–'} defaultMessage={typeValue} />{' '}
          {children}
        </Badge>
      </span>
    </Tooltip>
  );
}

export function Summary({ meta, ...rest }: SummaryProps) {
  const intl = useIntl();
  console.log('Summary component rendered with meta:', meta);
  return (
    <Group {...rest} gap='xs' align='center' mt={10} ml={0}>
      <MetaBadge typeName='listType' typeValue={meta.listType ?? 'No list type'} color='' icon={faBookmark} >
        {!intl.formatMessage({ id: meta.listType ?? 'No list type', defaultMessage: 'meta.listType' })?.toLowerCase().includes('list') && (
          <FormattedMessage id='summary.list.label' defaultMessage='list' />
        )}
      </MetaBadge>
      <MetaBadge typeName='ownerName' typeValue={meta.ownerName ?? '–'} icon={faUser} />
      {meta.licence && <MetaBadge typeName='licence' typeValue={meta.licence} icon={faCreativeCommons} />}
      {meta.region && <MetaBadge typeName='region' typeValue={meta.region} icon={faGlobe} />}
      {meta.authority && <MetaBadge typeName='authority' typeValue={meta.authority} icon={faBank} />}
      {((meta?.tags || []).length > 0) && meta!.tags.map((tag) => (
        <MetaBadge
          color='brown'
          typeName='tag'
          typeValue={tag}
          icon={faTag}
        />
      ))}
      {meta.dataResourceUid && <MetaBadge
        typeName='dataResourceUid'
        typeValue={meta.dataResourceUid}
        href={`${import.meta.env.VITE_ALA_COLLECTORY}/${meta.isPrivate ? 'dataResource' : 'public'}/show/${meta.dataResourceUid}`}
        icon={faIdBadge}
      />}
    </Group>
  );
}
