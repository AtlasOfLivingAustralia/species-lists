import { PropsWithChildren } from 'react';
import { useAuth } from 'react-oidc-context';
import { notifications } from '@mantine/notifications';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faLock } from '@fortawesome/free-solid-svg-icons';

import ALAContext from './ALAContext';
import { SpeciesList } from '../../api';
import rest from '#/api/rest';
import classes from '../../Notifications.module.css';

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

  // Centralized notification function
  const showAuthRequiredNotification = () => {
    notifications.show({
      id: 'auth-required',
      title: 'Login Required',
      message: 'You need to "Sign in" to access this page',
      withBorder: true,
      icon: <FontAwesomeIcon icon={faLock} fontSize={16}/>,
      autoClose: 10000,
      classNames: {
        root: classes.notification,
        title: classes.title,
        description: classes.description,
      },
    });
  };

  return (
    <ALAContext.Provider
      value={{
        auth,
        token: auth.isAuthenticated ? auth.user?.access_token : undefined,
        userid,
        roles,
        showAuthRequiredNotification,
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
