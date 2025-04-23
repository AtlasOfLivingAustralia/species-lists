// Global styles
import '@mantine/core/styles.css';
import '@mantine/nprogress/styles.css';
import '@mantine/notifications/styles.css';

import { theme } from '@atlasoflivingaustralia/ala-mantine';
import { MantineProvider } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import notificationStyles from './Notifications.module.css';

// Authentication
import { AuthProvider } from 'react-oidc-context';
import { WebStorageStateStore } from 'oidc-client-ts';
import handleCallback from './helpers/auth/handleCallback';
import { ALAProvider } from './helpers/context/ALAProvider';

// Internationalization
import { IntlProvider } from 'react-intl';
import en from './locale/en.json';

// Application
import App from './App';

// Use localStorage for user persistence
const userStore = new WebStorageStateStore({ store: localStorage });

function Main() {
  return (
    <AuthProvider
      client_id={import.meta.env.VITE_AUTH_CLIENT_ID}
      redirect_uri={import.meta.env.VITE_AUTH_REDIRECT_URI}
      authority={import.meta.env.VITE_AUTH_AUTHORITY}
      scope={import.meta.env.VITE_AUTH_SCOPE}
      userStore={userStore}
      onSigninCallback={handleCallback}
    >
      <MantineProvider theme={theme}>
        <IntlProvider messages={en} locale='en'>
          <ModalsProvider modalProps={{ radius: 'lg' }}>
            <ALAProvider>
              <Notifications
                transitionDuration={400}
                position="top-right"
                classNames={notificationStyles}
              />
              <App />
            </ALAProvider>
          </ModalsProvider>
        </IntlProvider>
      </MantineProvider>
    </AuthProvider>
  );
}

export default Main;
