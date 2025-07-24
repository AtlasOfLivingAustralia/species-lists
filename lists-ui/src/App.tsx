/// <reference types="vite-plugin-svgr/client" />

import { useEffect } from 'react';

// Routing
import { NuqsAdapter } from 'nuqs/adapters/react-router/v7';
import { RouterProvider } from 'react-router/dom';
import routes from './Router';

// Authentication
import { useAuth } from 'react-oidc-context';

// Local components
import PageLoader from './components/PageLoader';
import handleRefresh from './helpers/auth/handleRefresh';

function App() {
  const auth = useAuth();

  useEffect(() => {
    if (auth.isAuthenticated) {
      const refreshInterval = setInterval(async () => {
        console.log(auth.user?.expires_in);
        if ((auth.user?.expires_in || 0) < 60) await handleRefresh(auth);
      }, 1000);

      return () => clearInterval(refreshInterval);
    }
  }, [auth.isAuthenticated]);

  // If the user hasn't been authenticated, show a page loader instead
  return auth.isLoading ? (
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
