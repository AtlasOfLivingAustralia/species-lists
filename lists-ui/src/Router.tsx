import { lazy, Suspense } from 'react';
import { createBrowserRouter, redirect } from 'react-router';
import { jwtDecode } from 'jwt-decode';

// Views
import Dashboard from './views/Dashboard';
import Home from './views/Home';

// Page loader & error components
import PageLoader from './components/PageLoader';
import PageError from './components/PageError';

import { getAccessToken } from './helpers/utils/getAccessToken';

// Admin API
import adminApi from './api/rest/admin';

const JWT_ROLES = import.meta.env.VITE_AUTH_JWT_ROLES;
const JWT_ADMIN_ROLE = import.meta.env.VITE_AUTH_JWT_ADMIN_ROLE;

const List = lazy(() => import('./views/List'));

const router = createBrowserRouter([
  {
    path: '',
    element: <Dashboard />,
    errorElement: <PageError />,
    children: [
      {
        path: '',
        element: <Home />,
      },
      {
        path: 'list/:id',
        id: 'list',
        element: (
          <Suspense fallback={<PageLoader />}>
            <List />
          </Suspense>
        ),
        errorElement: <PageError />,
        children: [
          {
            path: 'reingest',
            lazy: () => import('./views/Reingest'),
          },
        ],
      },
      {
        // Legacy lists redirect
        path: 'speciesListItem/list/:id',
        loader: ({ params }) => redirect(`/list/${params.id}`),
      },
      {
        path: '/upload',
        lazy: () => import('./views/Upload'),
      },
      {
        path: '/admin',
        lazy: () => import('./views/Admin'),
        hydrateFallbackElement: <PageLoader />,
        loader: async () => {
          // Fetch the access token
          const token = getAccessToken();
          if (!token) return redirect('/');

          // Ensure the user is an admin
          const parsed = jwtDecode(token) as any;
          if (!parsed[JWT_ROLES] || !parsed[JWT_ROLES].includes(JWT_ADMIN_ROLE))
            return redirect('/');

          try {
            const admin = adminApi(token);
            return await admin.migrateProgress();
          } catch (error) {
            console.log(error);
            return redirect('/');
          }
        },
        errorElement: <PageError />,
      },
      {
        path: '*',
        loader: () => {
          return redirect('/');
        },
      },
    ],
  },
]);

export default router;
