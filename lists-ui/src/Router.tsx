import { jwtDecode } from 'jwt-decode';
import { lazy, Suspense } from 'react';
import { createBrowserRouter, redirect } from 'react-router';

// Views
import Dashboard from './views/Dashboard';
import Home from './views/Home';
import List from './views/List';

// Page loader & error components
import PageError from './components/PageError';
import PageLoader from './components/PageLoader';

import { getAccessToken } from './helpers/utils/getAccessToken';

// Admin API
import { ProtectedRoute } from './components/ProtectedRoute';

const UploadPage = lazy(() => import('./views/Upload'));
const ReingestPage = lazy(() => import('./views/Reingest'));
const AdminPage = lazy(() => import('./views/Admin'));

// List is loaded eagerly so its header skeleton renders immediately while data fetches.

// Wrap the Upload component with ProtectedRoute and Suspense
const ProtectedUpload = () => (
  <ProtectedRoute>
    <Suspense fallback={<div>Loading...</div>}>
      <UploadPage />
    </Suspense>
  </ProtectedRoute>
);

// Create a protected and suspended wrapper for the Reingest page
const ProtectedReingest = () => (
  <ProtectedRoute>
    <Suspense fallback={<PageLoader />}> {/* Use PageLoader for better consistency */}
      <ReingestPage />
    </Suspense>
  </ProtectedRoute>
);

// Create a protected and suspended wrapper for the Admin page (ROLE_ADMIN only)
const ProtectedAdmin = () => (
  <ProtectedRoute adminOnly>
    <Suspense fallback={<PageLoader />}>
      <AdminPage />
    </Suspense>
  </ProtectedRoute>
);

const notFoundLoader = () => {
  throw new Response('Not Found', { status: 404 });
};
const router = createBrowserRouter([
  {
    // Redirect legacy SDS URL to new filter format
    path: 'public/speciesLists',
    loader: ({ request }) => {
      const url = new URL(request.url);
      const filters: string[] = [];
      
      // Handle all filter params: isSDS, isBIE, isAuthoritative, isThreatened, isInvasive, isBiosecurity
      const filterParams = ['isSDS', 'isBIE', 'isAuthoritative', 'isThreatened', 'isInvasive', 'isBiosecurity'];
      
      filterParams.forEach((param) => {
        const value = url.searchParams.get(param);
        if (value === 'eq:true') {
          filters.push(`${param}:true`);
        }
      });
      
      if (filters.length > 0) {
        return redirect(`/?filters=${filters.join(',')}`);
      }
      
      // Redirect to home if no matching filters
      return redirect('/');
    },
  },
  {
    path: '',
    element: <Dashboard />,
    errorElement: <PageError />,
    children: [
      {
        path: '',
        element: <Home routeId="home"/>,
      },
      {
        path: 'my-lists',
        element: (
          <ProtectedRoute>
            <Suspense fallback={<PageLoader />}> 
              <Home routeId="my-lists"/>
            </Suspense>
          </ProtectedRoute>
        ),
      },
      {
        path: 'admin-lists',
        element: <Home routeId="admin-lists"/>,
      },
      {
        path: 'list/:id',
        id: 'list',
        element: <List />,
        errorElement: <PageError />,
        children: [
          {
            path: 'reingest',
            // Use the ProtectedReingest wrapper for this route
            element: <ProtectedReingest />,
            errorElement: <PageError />, // Add error element here as well
          },
        ],
      },
      {
        // Legacy list with ID redirect
        path: 'speciesListItem/list/:id',
        loader: ({ params }) => redirect(`/list/${params.id}`),
      },
      {
        // Legacy my lists redirect
        path: 'speciesList/list',
        loader: () => redirect(`/my-lists`),
      },
      {
        path: '/upload',
        element: <ProtectedUpload />,
      },
      {
        path: '/admin',
        element: <ProtectedAdmin />,
        errorElement: <PageError />,
      },
      {
        path: '/iconic-species',
        loader: () => {
          return redirect(import.meta.env.VITE_ALA_ICONIC_SPECIES_PAGE || '/');
        },
      },
      {
        path: '*',
        loader: notFoundLoader,
        errorElement: <PageError />,
      }
    ],
  },
]);

export default router;
