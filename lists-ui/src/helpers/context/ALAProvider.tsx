import { PropsWithChildren } from 'react';
import { useAuth } from 'react-oidc-context';
import { SpeciesList } from '../../api';

// Import the context
import ALAContext from './ALAContext';
import rest from '#/api/rest';

export const ALAProvider = ({ children }: PropsWithChildren) => {
  const auth = useAuth();

  // Extract the user
  const userid = (auth.user?.profile['cognito:username'] || '') as string;
  const roles = (auth.user?.profile['cognito:groups'] || []) as string[];
  const isAdmin = auth.isAuthenticated && roles.includes('admin');

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
