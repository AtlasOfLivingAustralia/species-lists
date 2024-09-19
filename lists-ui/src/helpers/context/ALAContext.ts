import { createContext } from 'react';
import { AuthContextProps } from 'react-oidc-context';

// APIs
import { SpeciesList } from '#/api';
import rest from '#/api/rest';

export interface ALAContextProps {
  auth: AuthContextProps | null;
  token: string;
  userid: string;
  roles: string[];
  rest: ReturnType<typeof rest>;
  isAdmin: boolean;
  isAuthenticated: boolean;
  isAuthorisedForList: (list: SpeciesList) => boolean;
}

export default createContext<ALAContextProps | null>({
  auth: null,
  token: '',
  userid: '',
  roles: [],
  rest: rest('', false),
  isAdmin: false,
  isAuthenticated: false,
  isAuthorisedForList: () => false,
});
