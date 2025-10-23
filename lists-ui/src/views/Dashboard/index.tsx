import {
  Footer,
  Header,
  IndigenousAcknowledgement,
} from '@atlasoflivingaustralia/ala-mantine';
import { Divider } from '@mantine/core';
import { useEffect } from 'react';

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
import { ExternalBanner } from '#/components/ExternalBanner';
import { useAuth } from 'react-oidc-context';
import handleSignout from '../../helpers/auth/handleSignout';

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
        homeUrl={import.meta.env.VITE_ALA_HOME_PAGE || ''}
        onSearchClick={() => (window.location.href = 'https://bie.ala.org.au')
        }
        fullWidth 
        compact
        myProfileUrl={import.meta.env.VITE_ALA_USER_PROFILE || ''}
      />
      <Divider />
      <Outlet />
      <Divider mt='xl' />
      <Footer fullWidth />
      <IndigenousAcknowledgement />
    </>
  );
}

export default Dashboard;
