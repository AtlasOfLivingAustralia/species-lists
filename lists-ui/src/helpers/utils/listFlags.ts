import {
  faBiohazard,
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
    description: 'Authoritative lists in ALA Biocache',
    icon: faShield,
    admin: true,
  },
  {
    flag: 'isBiosecurity',
    label: 'Biosecurity',
    description: 'Lists that support biosecurity',
    icon: faBiohazard,
    admin: true,
  },
  {
    flag: 'isInvasive',
    label: 'Invasive',
    description: 'Lists of invasive species',
    icon: faBug,
    admin: true,
  },
  {
    flag: 'isThreatened',
    label: 'Threatened',
    description: 'Lists of threatened species',
    icon: faExclamationCircle,
    admin: true,
  },
  {
    flag: 'isSDS',
    label: 'Sensitive',
    description: 'Lists in ALA SDS',
    icon: faLock,
    admin: true,
  },
  {
    flag: 'isBIE',
    label: 'Species pages',
    description: 'Lists included on species pages',
    icon: faRightLeft,
    admin: true,
  },
];
