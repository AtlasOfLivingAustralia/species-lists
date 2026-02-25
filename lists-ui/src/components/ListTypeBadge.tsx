import sanitiseText from "#/helpers/utils/sanitiseText";
import AlaIcon from '#/static/ala-logo-grey.svg?react';
import { faMap, faStar } from '@fortawesome/free-regular-svg-icons';
import { faCircleExclamation, faCircleRadiation, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { Box, Text, Tooltip } from "@mantine/core";
import { FormattedMessage, useIntl } from "react-intl";

const listTypeValues: Record<string, React.ReactNode> = {
  "isAuthoritative": <FontAwesomeIcon icon={faStar} fontSize={16} color='grey'/>,
  "isSDS": <FontAwesomeIcon icon={faShieldHalved} fontSize={16} color='grey'/>,
  "isBIE": <AlaIcon color='grey' width="22px" height="18px"/>,
  "hasRegion": <FontAwesomeIcon icon={faMap} fontSize={16} color='grey'/>,
  "isThreatened": <FontAwesomeIcon icon={faCircleExclamation} fontSize={16} color='grey'/>,
  "isInvasive": <FontAwesomeIcon icon={faCircleRadiation} fontSize={16} color='grey'/>,
};

export function ListTypeBadge({
  listTypeValue, 
  titleText,
  tooltipText: tooltipTextProp,
  iconSide = 'left'
} : {
  listTypeValue: string, 
  titleText?: string | React.ReactNode,
  /** Explicit tooltip override. When provided, skips the i18n licence.* lookup. */
  tooltipText?: string,
  iconSide?: 'left' | 'right'}) 
{
  const intl = useIntl();
  const listIcon = listTypeValues[listTypeValue] || null;
  // sanitise the listTypeValue to prevent XSS attacks
  const sanitisedListTypeValue = sanitiseText(listTypeValue);
  const message = <FormattedMessage id={sanitisedListTypeValue || 'filter.key.missing'} defaultMessage={sanitisedListTypeValue}/>;

  // Use the explicit tooltip when provided; otherwise fall back to the i18n licence.* lookup
  const resolvedTooltip = tooltipTextProp ?? intl.formatMessage({
    id: `licence.${titleText && typeof titleText === 'string' ? sanitiseText(titleText) : 'none'}`,
    defaultMessage: (typeof titleText === 'string' ? titleText : '') || ''
  });

  return (
    <Box style={{ display: 'flex', alignItems: 'center', overflow: 'hidden', whiteSpace: 'nowrap', maxWidth: '100%' }}>
      {iconSide === 'left' && listIcon || ''}
      <Text
        size='sm'
        fw='400'
        pl={listIcon && iconSide === 'left' ? 5 : 0}
        component='span'
      >
        {titleText && typeof titleText === 'string' && titleText.length > 1 && resolvedTooltip !== titleText ? (
          <Tooltip label={resolvedTooltip} withArrow position="right">
            <span>{titleText}</span>
          </Tooltip>
        ) : (
          titleText || message
        )}
      </Text>
      {iconSide === 'right' && (
        <Box style={{ display: 'flex', alignItems: 'center', paddingLeft: 6  }}>
          {listIcon || ''}
        </Box>
        )}
    </Box>
  );
}