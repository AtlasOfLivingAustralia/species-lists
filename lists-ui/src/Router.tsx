import { createBrowserRouter, redirect } from 'react-router';

// Views
import Dashboard from './views/Dashboard';
import Home from './views/Home';

// Page loader & error components
import PageLoader from './components/PageLoader';
import PageError from './components/PageError';

import { performGQLQuery, queries } from './api';
import { getAccessToken } from './helpers/utils/getAccessToken';

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
        loader: async ({ params }) => {
          const token = getAccessToken();
          const data = await performGQLQuery(
            queries.QUERY_LISTS_GET,
            {
              speciesListID: params.id,
            },
            token
          );

          if (data.meta === null || data.list === null) {
            throw new Response('List not found', { status: 404 });
          }

          return data;
        },
        errorElement: <PageError />,
      },
      {
        path: '/upload',
        lazy: () => import('./views/Upload'),
      },
      {
        path: '/admin',
        lazy: () => import('./views/Admin'),
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
