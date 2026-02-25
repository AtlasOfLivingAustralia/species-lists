import { createContext } from 'react';

// APIs
import { SpeciesList } from '#/api';
import rest from '#/api/rest';

export interface ALAContextProps {
  token?: string;
  userid: string;
  roles: string[];
  rest: ReturnType<typeof rest>;
  isAdmin: boolean;
  isAuthenticated: boolean;
  isAuthorisedForList: (list: SpeciesList) => boolean;
  showAuthRequiredNotification: () => void;
}

export default createContext<ALAContextProps | null>({
  token: undefined,
  userid: '',
  roles: [],
  rest: rest('', false),
  isAdmin: false,
  isAuthenticated: false,
  isAuthorisedForList: () => false,
  showAuthRequiredNotification: () => {},
});
