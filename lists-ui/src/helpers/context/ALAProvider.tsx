import { faLock } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { notifications } from '@mantine/notifications';
import { PropsWithChildren } from 'react';
import { useAuth } from 'react-oidc-context';

import rest from '#/api/rest';
import { useIntl } from 'react-intl';
import { SpeciesList } from '../../api';
import classes from '../../Notifications.module.css';
import ALAContext from './ALAContext';

const JWT_ROLES = import.meta.env.VITE_AUTH_JWT_ROLES;
const JWT_USERID = import.meta.env.VITE_AUTH_JWT_USERID;
const JWT_ADMIN_ROLE = import.meta.env.VITE_AUTH_JWT_ADMIN_ROLE;

export const ALAProvider = ({ children }: PropsWithChildren) => {
  const auth = useAuth();
  const intl = useIntl();

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
      title: intl.formatMessage({ id: 'login.required.title', defaultMessage: 'Login required' }),
      message: intl.formatMessage({ id: 'login.required.description', defaultMessage: 'You need to "Sign in" to access this page' }),
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
        rest: rest(auth.isAuthenticated ? auth.user?.access_token || '' : '', isAdmin),
        isAdmin,
        isAuthenticated: auth.isAuthenticated,
        isAuthorisedForList,
      }}
    >
      {children}
    </ALAContext.Provider>
  );
};
