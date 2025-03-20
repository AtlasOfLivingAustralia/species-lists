import { createBrowserRouter, redirect } from 'react-router';
import { jwtDecode } from 'jwt-decode';

// Views
import Dashboard from './views/Dashboard';
import Home from './views/Home';

// Page loader & error components
import PageLoader from './components/PageLoader';
import PageError from './components/PageError';

import { performGQLQuery, queries } from './api';
import { getAccessToken } from './helpers/utils/getAccessToken';

// Admin API
import adminApi from './api/rest/admin';
import {
  parseAsBoolean,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from 'nuqs';
import { parseAsFilters } from './helpers';

const JWT_ROLES = import.meta.env.VITE_AUTH_JWT_ROLES;
const JWT_ADMIN_ROLE = import.meta.env.VITE_AUTH_JWT_ADMIN_ROLE;

enum SortDirection {
  ASC = 'asc',
  DESC = 'desc',
}

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
        lazy: () => import('./views/List'),
        hydrateFallbackElement: <PageLoader />,
        loader: async ({ request, params }) => {
          const token = getAccessToken();
          const searchParams = new URL(request.url).searchParams;

          const data = await performGQLQuery(
            queries.QUERY_LISTS_GET,
            {
              speciesListID: params.id,
              searchQuery: parseAsString.parse(
                searchParams.get('search') || ''
              ),
              page: parseAsInteger.parse(searchParams.get('page') || '0'),
              size: parseAsInteger.parse(searchParams.get('size') || '10'),
              filters: parseAsFilters.parse(searchParams.get('filters') || ''),
              isPrivate: parseAsBoolean.parse(
                searchParams.get('isPrivate') || 'false'
              ),
              sort: parseAsString.parse(
                searchParams.get('sort') || 'scientificName'
              ),
              dir: parseAsStringEnum<SortDirection>(
                Object.values(SortDirection)
              ).parse(searchParams.get('dir') || 'asc'),
            },
            token
          );

          if (data.meta === null || data.list === null) {
            throw new Response('List not found', { status: 404 });
          }

          return data;
        },
        errorElement: <PageError />,
        children: [
          {
            path: 'reingest',
            lazy: () => import('./views/Reingest'),
          },
        ],
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
