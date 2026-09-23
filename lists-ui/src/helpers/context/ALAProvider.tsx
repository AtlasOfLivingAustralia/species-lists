import { faLock, faShield } from '@fortawesome/free-solid-svg-icons';
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
const JWT_EDITOR_ROLE = import.meta.env.VITE_AUTH_JWT_EDITOR_ROLE;

export const ALAProvider = ({ children }: PropsWithChildren) => {
  const auth = useAuth();
  const intl = useIntl();

  // Extract the user
  const userid = (auth.user?.profile[JWT_USERID] || '') as string;
  const rawRoles = auth.user?.profile[JWT_ROLES] ?? [];
  const roles = (Array.isArray(rawRoles) ? rawRoles : [rawRoles]) as string[];
  const isAdmin = auth.isAuthenticated && roles.includes(JWT_ADMIN_ROLE);
  const isAdminOrEditor =
    auth.isAuthenticated && (isAdmin || (JWT_EDITOR_ROLE ? roles.includes(JWT_EDITOR_ROLE) : false));

  const isAuthorisedForList = (list: SpeciesList) =>
    auth.isAuthenticated && (isAdmin || list.owner === userid);

  // Centralized notification function
  const showAuthRequiredNotification = (type: 'auth' | 'admin' = 'auth') => {
    const isAdminType = type === 'admin';
    notifications.show({
      id: isAdminType ? 'admin-required' : 'auth-required',
      color: isAdminType ? 'red' : undefined,
      title: intl.formatMessage({
        id: isAdminType ? 'admin.required.title' : 'login.required.title',
        defaultMessage: isAdminType ? 'Access denied' : 'Login required',
      }),
      message: intl.formatMessage({
        id: isAdminType ? 'admin.required.description' : 'login.required.description',
        defaultMessage: isAdminType
          ? 'You need administrator privileges to access this page.'
          : 'You need to "Sign in" to access this page',
      }),
      withBorder: true,
      icon: (
        <FontAwesomeIcon
          icon={isAdminType ? faShield : faLock}
          fontSize={16}
          color={isAdminType ? 'white' : undefined}
        />
      ),
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
        token: auth.isAuthenticated ? auth.user?.access_token : undefined,
        userid,
        roles,
        showAuthRequiredNotification,
        rest: rest(auth.isAuthenticated ? auth.user?.access_token || '' : '', isAdmin),
        isAdmin,
        isAdminOrEditor,
        isAuthenticated: auth.isAuthenticated,
        isAuthorisedForList,
      }}
    >
      {children}
    </ALAContext.Provider>
  );
};
