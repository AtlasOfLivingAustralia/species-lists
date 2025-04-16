import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { Box, Text } from "@mantine/core";
import { FormattedMessage } from "react-intl";
import { faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { faMap, faStar } from '@fortawesome/free-regular-svg-icons';
import AlaIcon from '#/static/ala-logo-grey.svg?react';

const listTypeValues: { [key: string]: JSX.Element } = {
  "isAuthoritative": <FontAwesomeIcon icon={faStar} fontSize={16} color='grey'/>,
  "isSDS": <FontAwesomeIcon icon={faShieldHalved} fontSize={16} color='grey'/>,
  "isBIE": <AlaIcon color='grey' width="22px" height="18px"/>,
  "hasRegion": <FontAwesomeIcon icon={faMap} fontSize={16} color='grey'/>, // <i class="fa-regular fa-map"></i>
};

export function ListTypeBadge({listTypeValue, iconSide = 'left'} : {listTypeValue: string, iconSide?: 'left' | 'right'}) {
  const listIcon = listTypeValues[listTypeValue] || null;

  return (
    <Box style={{ display: 'flex', alignItems: 'center' }}>
      {iconSide === 'left' && listIcon || ''}
      <Text
        size='sm' 
        fw='400' 
        pl={listIcon && iconSide === 'left' ? 6 : 0} 
        component="span"
      >
        <FormattedMessage id={listTypeValue || 'filter.key.missing'} defaultMessage={listTypeValue}/>
      </Text>
      {iconSide === 'right' && (
        <Box style={{ display: 'flex', alignItems: 'center', paddingLeft: 8  }}>
          {listIcon || ''}
        </Box>
        )}
    </Box>
  );
}