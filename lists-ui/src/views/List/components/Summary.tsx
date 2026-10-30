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
  const intl = useIntl();
  const { constraints } = useConstraints(ala);

  // These resolve to undefined until loaded — that's fine, we fall back to raw meta values
  const listType    = constraints?.listType?.find(t => t.value === meta.listType);
  const listLicence = constraints?.licence?.find(l => l.value === meta.licence);
  const listRegion  = constraints?.region?.find(r => r.value === meta.region);

  // Translate a value via the locale files (same pattern as ListMeta); null when no key exists
  const tr = (key: string) => (intl.messages[key] ? intl.formatMessage({ id: key }) : null);

  const listTypeLabel = tr(meta.listType) ?? listType?.label ?? meta.listType;
  const licenceValue = listLicence?.value ?? meta.licence;
  const licenceLabel = tr(`licence.${licenceValue}`) ?? licenceValue;
  const regionLabel = tr(`region.${meta.region}`) ?? listRegion?.label ?? meta.region;

  return (
    <Group {...rest} gap='xs' align='center' mt={10} ml={0}>

      {/* Render immediately with raw value; swap in resolved label when constraints load */}
      {meta.listType && (
        <MetaBadge
          typeName='listType'
          typeValue={listTypeLabel}
          color=''
          icon={faBookmark}
        />
      )}

      <MetaBadge typeName='ownerName' typeValue={meta.ownerName ?? '–'} icon={faUser} />

      {/* Licence: the pill keeps the short code (same in every language);
          the tooltip shows the full translated name via the licence.* keys */}
      {meta.licence && (
        <MetaBadge
          typeName='licence'
          typeValue={licenceValue}
          icon={faCreativeCommons}
          href={listLicence ? generateCCLink(listLicence.value) : undefined}
          title={licenceLabel}
        />
      )}

      {/* Region: translated country name via the region.* keys */}
      {meta.region && (
        <MetaBadge
          typeName='region'
          typeValue={regionLabel}
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