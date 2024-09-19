import PageLoader from './components/PageLoader';

// Routing
import { RouterProvider } from 'react-router-dom';
import routes from './Router';

// Authentication
import { useAuth } from 'react-oidc-context';

function App() {
  const auth = useAuth();

  // If the user hasn't been authenticated, show a page loader instead
  return auth.isLoading ? (
    <div style={{ width: '100vw', height: '100vh' }}>
      <PageLoader />
    </div>
  ) : (
    <RouterProvider router={routes} />
  );
}

export default App;
