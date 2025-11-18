import sanitiseText from "#/helpers/utils/sanitiseText";
import AlaIcon from '#/static/ala-logo-grey.svg?react';
import { faMap, faStar } from '@fortawesome/free-regular-svg-icons';
import { faCircleExclamation, faCircleRadiation, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { Box, Text, Tooltip } from "@mantine/core";
import { FormattedMessage } from "react-intl";

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
  iconSide = 'left'
} : {
  listTypeValue: string, 
  titleText?: string,
  iconSide?: 'left' | 'right'}) 
{
  const listIcon = listTypeValues[listTypeValue] || null;
  // sanitise the listTypeValue to prevent XSS attacks
  const sanitisedListTypeValue = sanitiseText(listTypeValue);

  const message = <FormattedMessage id={sanitisedListTypeValue || 'filter.key.missing'} defaultMessage={sanitisedListTypeValue}/>;

  return (
    <Box style={{ display: 'flex', alignItems: 'center', overflow: 'hidden', whiteSpace: 'nowrap', maxWidth: '100%' }}>
      {iconSide === 'left' && listIcon || ''}
      <Text
        size='sm'
        fw='400'
        pl={listIcon && iconSide === 'left' ? 5 : 0}
        component='span'
      >
        {titleText && titleText.length > 1 ? (
          <Tooltip label={titleText} withArrow position="right">
            <span>{message}</span>
          </Tooltip>
        ) : (
          message
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