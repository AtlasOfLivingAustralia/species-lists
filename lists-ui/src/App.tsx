/// <reference types="vite-plugin-svgr/client" />

import { useEffect, useState } from 'react';

// Routing
import { RouterProvider } from 'react-router/dom';
import { NuqsAdapter } from 'nuqs/adapters/react-router/v7';
import routes from './Router';

// Authentication
import { useAuth } from 'react-oidc-context';
import handleSignout from './helpers/auth/handleSignout';

// Local components
import PageLoader from './components/PageLoader';

function App() {
  const auth = useAuth();
  const [silentRenew, setSilentRenew] = useState<boolean>(false);

  useEffect(() => {
    if (auth.isAuthenticated) {
      // Create an interval to silently refresh the token
      const interval = setInterval(async () => {
        if (
          auth.isAuthenticated &&
          !auth.isLoading &&
          (auth.user?.expires_in || 0) <= 60
        ) {
          setSilentRenew(true);

          try {
            await auth.signinSilent();
          } catch (error) {
            // Sign out if the token renewal wasn't successful
            handleSignout(auth);
          }

          setSilentRenew(false);
        }
      }, 1000);

      return () => clearInterval(interval);
    }
  }, [auth]);

  // If the user hasn't been authenticated, show a page loader instead
  return auth.isLoading && !silentRenew ? (
    <div style={{ width: '100vw', height: '100vh' }}>
      <PageLoader />
    </div>
  ) : (
    <NuqsAdapter>
      <RouterProvider router={routes} />
    </NuqsAdapter>
  );
}

export default App;
