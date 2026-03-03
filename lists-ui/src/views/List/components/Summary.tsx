import {
  Badge,
  Group,
  GroupProps,
  Tooltip
} from '@mantine/core';
import { useIntl } from 'react-intl';

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
import { useALA } from '#/helpers/context/useALA';
import { useConstraints } from '#/api/graphql/useConstraints';
import { generateCCLink } from '#/helpers/utils/generateCCLinks';

interface SummaryProps extends GroupProps {
  meta: SpeciesList;
}

function MetaBadge({
  typeName,
  typeValue,
  title,
  href,
  color = 'default',
  icon,
  children,
}: {
  typeName?: string;
  typeValue: string;
  title?: string;
  href?: string;
  color?: string;
  icon: IconDefinition;
  children?: React.ReactNode;
}) {
  const intl = useIntl();

  if (!typeValue) {
    // empty value, don't render badge
    return null;
  }

  return (
    <Tooltip
      position='bottom'
      label={title || intl.formatMessage({ id: 'summary.' + typeName + '.tooltip', defaultMessage: typeName })}
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
          {...(href ? { href, target: '_blank', rel: 'noopener noreferrer' } : {})}
        >
          {typeValue}
          {children}
        </Badge>
      </span>
    </Tooltip>
  );
}

export function Summary({ meta, ...rest }: SummaryProps) {
  const ala = useALA();
  const { constraints } = useConstraints(ala);

  // These resolve to undefined until loaded — that's fine, we fall back to raw meta values
  const listType    = constraints?.listType?.find(t => t.value === meta.listType);
  const listLicence = constraints?.licence?.find(l => l.value === meta.licence);
  const listRegion  = constraints?.region?.find(r => r.value === meta.region);

  return (
    <Group {...rest} gap='xs' align='center' mt={10} ml={0}>

      {/* Render immediately with raw value; swap in resolved label when constraints load */}
      {meta.listType && (
        <MetaBadge
          typeName='listType'
          typeValue={listType?.label ?? meta.listType}
          title={`List type: ${listType?.label ?? meta.listType}`}
          color=''
          icon={faBookmark}
        />
      )}

      <MetaBadge typeName='ownerName' typeValue={meta.ownerName ?? '–'} icon={faUser} />

      {/* Licence: only meaningful if we have a resolved label (to build the CC link),
          but show the raw value as a fallback while loading */}
      {meta.licence && (
        <MetaBadge
          typeName='licence'
          typeValue={listLicence?.value ?? meta.licence}
          icon={faCreativeCommons}
          href={listLicence ? generateCCLink(listLicence.value) : undefined}
          title={`Licence: ${listLicence?.label ?? meta.licence}`}
        />
      )}

      {meta.region && (
        <MetaBadge
          typeName='region'
          typeValue={listRegion?.value ?? meta.region}
          title={`Region: ${listRegion?.label ?? meta.region}`}
          icon={faGlobe}
        />
      )}

      {meta.authority && (
        <MetaBadge typeName='authority' typeValue={meta.authority} icon={faBank} />
      )}

      {/* Tags: render with raw value immediately, swap label in when loaded */}
      {meta.tags?.map((tag) => {
        const tagOption = constraints?.tags?.find(t => t.value === tag);
        return (
          <MetaBadge
            key={tag}
            color='brown'
            typeName='tag'
            typeValue={tagOption?.label ?? tag}
            icon={faTag}
          />
        );
      })}

      {meta.dataResourceUid && (
        <MetaBadge
          typeName='dataResourceUid'
          typeValue={meta.dataResourceUid}
          href={`${import.meta.env.VITE_ALA_COLLECTORY}/${meta.isPrivate ? 'dataResource' : 'public'}/show/${meta.dataResourceUid}`}
          icon={faIdBadge}
        />
      )}
    </Group>
  );
}