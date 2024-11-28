import {
  faBug,
  faExclamationCircle,
  faLock,
  faRightLeft,
  faShield,
} from '@fortawesome/free-solid-svg-icons';

export default [
  {
    flag: 'isAuthoritative',
    label: 'Authoritative',
    description: 'Verified by the ALA',
    icon: faShield,
    admin: true,
  },
  {
    flag: 'isInvasive',
    label: 'Invasive',
    description: 'List taxa are invasive',
    icon: faBug,
    admin: false,
  },
  {
    flag: 'isThreatened',
    label: 'Threatened',
    description: 'List taxa are threatened',
    icon: faExclamationCircle,
    admin: false,
  },
  {
    flag: 'isSDS',
    label: 'Sensitive',
    description: 'Used in the SDS',
    icon: faLock,
    admin: true,
  },
  {
    flag: 'isBIE',
    label: 'BIE',
    description: 'Display on species pages',
    icon: faRightLeft,
    admin: true,
  },
];
