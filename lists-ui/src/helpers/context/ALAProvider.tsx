import { PropsWithChildren } from 'react';
import { useAuth } from 'react-oidc-context';
import { SpeciesList } from '../../api';

// Import the context
import ALAContext from './ALAContext';
import rest from '#/api/rest';

const JWT_ROLES = import.meta.env.VITE_AUTH_JWT_ROLES;
const JWT_USERID = import.meta.env.VITE_AUTH_JWT_USERID;
const JWT_ADMIN_ROLE = import.meta.env.VITE_AUTH_JWT_ADMIN_ROLE;

export const ALAProvider = ({ children }: PropsWithChildren) => {
  const auth = useAuth();

  // Extract the user
  const userid = (auth.user?.profile[JWT_USERID] || '') as string;
  const roles = (auth.user?.profile[JWT_ROLES] || []) as string[];
  const isAdmin = auth.isAuthenticated && roles.includes(JWT_ADMIN_ROLE);

  const isAuthorisedForList = (list: SpeciesList) =>
    auth.isAuthenticated && (isAdmin || list.owner === userid);

  return (
    <ALAContext.Provider
      value={{
        auth,
        token: auth.isAuthenticated ? auth.user?.access_token : undefined,
        userid,
        roles,
        rest: rest(auth.user?.access_token || '', isAdmin),
        isAdmin,
        isAuthenticated: auth.isAuthenticated,
        isAuthorisedForList,
      }}
    >
      {children}
    </ALAContext.Provider>
  );
};
