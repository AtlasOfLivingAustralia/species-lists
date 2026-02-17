// Global styles
import '@atlasoflivingaustralia/ala-mantine/styles';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/nprogress/styles.css';

import { theme } from '@atlasoflivingaustralia/ala-mantine';
import { MantineProvider } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import notificationStyles from './Notifications.module.css';

// Authentication
import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { AuthProvider } from 'react-oidc-context';
import { ALAProvider } from './helpers/context/ALAProvider';

// Internationalization
import { IntlProvider } from 'react-intl';
import en from './locale/en.json';

// Application
import App from './App';
import router from './Router';

export const userManager = new UserManager({
  authority: import.meta.env.VITE_AUTH_AUTHORITY,
  client_id: import.meta.env.VITE_AUTH_CLIENT_ID,
  redirect_uri: import.meta.env.VITE_AUTH_REDIRECT_URI,
  scope: import.meta.env.VITE_AUTH_SCOPE,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  automaticSilentRenew: false,
});

function Main() {

  async function handleCallback(user: User | void) {
    // If there's a user, it's a sign-in callback
    if (user) {
      const targetUrl = (user?.state as any)?.targetUrl || '/';
      await router.navigate(targetUrl, { replace: true });
    } else {
      window.history.replaceState({}, document.title, '/');
    }
  }

  return (
    <AuthProvider userManager={userManager} onSigninCallback={handleCallback}>
      <MantineProvider theme={theme}>
        <IntlProvider messages={en} locale='en'>
          <ModalsProvider modalProps={{ radius: 'lg' }}>
            <ALAProvider>
              <Notifications
                transitionDuration={400}
                position='top-right'
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
