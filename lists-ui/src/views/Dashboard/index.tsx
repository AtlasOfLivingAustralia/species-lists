import { useEffect } from 'react';
import { Divider } from '@mantine/core';
import {
  Header,
  Footer,
  IndigenousAcknowledgement,
} from '@atlasoflivingaustralia/ala-mantine';

// Navigation
import {
  NavigationProgress,
  completeNavigationProgress,
  resetNavigationProgress,
  startNavigationProgress,
} from '@mantine/nprogress';

// Routing
import { Outlet, useNavigation } from 'react-router';

// Authentication
import { useAuth } from 'react-oidc-context';
import handleSignout from '../../helpers/auth/handleSignout';
import { Breadcrumbs } from './components/Breadcrumbs';
import { ExternalBanner } from '#/components/ExternalBanner';

function Dashboard() {
  const auth = useAuth();
  const { state } = useNavigation();

  // Effect handler for navigation process indicator
  useEffect(() => {
    if (state === 'loading') {
      resetNavigationProgress();
      startNavigationProgress();
    } else {
      completeNavigationProgress();
    }
  }, [state]);

  return (
    <>
      <NavigationProgress
        stepInterval={20}
        aria-label='Navigation progress bar'
        portalProps={{ 'aria-hidden': true }}
      />
      <ExternalBanner
        url={import.meta.env.VITE_ALA_MESSAGES}
        services={['species-lists']}
      />
      <Header
        isAuthenticated={auth.isAuthenticated}
        onAuthClick={() => {
          if (auth.isAuthenticated) {
            handleSignout(auth);
          } else {
            auth.signinRedirect();
          }
        }}
        onSearchClick={() => (window.location.href = 'https://bie.ala.org.au')}
      />
      <Divider />
      <Breadcrumbs />
      <Outlet />
      <Divider mt='xl' />
      <Footer />
      <IndigenousAcknowledgement />
    </>
  );
}

export default Dashboard;
