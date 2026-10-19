// Global styles
import '@atlasoflivingaustralia/ala-mantine/styles';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/nprogress/styles.css';

import { theme } from '@atlasoflivingaustralia/ala-mantine';
import { DEFAULT_THEME, MantineProvider, MantineThemeOverride, mergeThemeOverrides } from '@mantine/core';
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
import { getAppTitlePrefix } from './helpers/utils/branding';
import {
  injectFathom,
  injectThemeBaseCss,
  injectThemeCss,
  primaryColorOverride,
  setFavicon,
} from './helpers/utils/runtimeTheme';

// Portal look & feel that a deployment can change without rebuilding (see runtimeTheme.ts). With
// none of these set, as in the ALA build, the app looks as it always has.
document.title = getAppTitlePrefix();
setFavicon(import.meta.env.VITE_FAVICON_URL);
injectThemeBaseCss(import.meta.env.VITE_THEME_BASE_CSS_URL);
injectThemeCss(import.meta.env.VITE_THEME_CSS_URL);
injectFathom(import.meta.env.VITE_APP_FATHOM_ID);

const primaryColor = primaryColorOverride(import.meta.env.VITE_THEME_PRIMARY_COLOR, [
  ...Object.keys(DEFAULT_THEME.colors),
  ...Object.keys(theme.colors ?? {}),
]);
const appTheme = primaryColor
  ? mergeThemeOverrides(theme, primaryColor as MantineThemeOverride)
  : theme;

export const userManager = new UserManager({
  authority: import.meta.env.VITE_AUTH_AUTHORITY,
  client_id: import.meta.env.VITE_AUTH_CLIENT_ID,
  redirect_uri: import.meta.env.VITE_AUTH_REDIRECT_URI,
  scope: import.meta.env.VITE_AUTH_SCOPE,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
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
      <MantineProvider theme={appTheme}>
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
