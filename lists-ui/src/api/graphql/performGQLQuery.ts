/* eslint-disable @typescript-eslint/no-explicit-any */
type Variables = { [key: string]: any };

async function performGQLQuery<T = any>(
  query: string,
  variables?: Variables,
  token?: string
): Promise<T> {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const response = await fetch(
    import.meta.env.VITE_API_BASEURL + import.meta.env.VITE_API_GRAPHQL,
    {
      method: 'POST',
      headers,
      body: JSON.stringify({
        query,
        operationName: query.match(/(?<=mutation |query )[a-zA-Z]+/g)?.[0],
        variables: variables || {},
      }),
    }
  );

  const data = await response.json();
  if (response.ok) {
    if (data.errors) {
      // If errorData is populated, we've recieved an error from the GraphQL server
      const [error] = data?.errors || [];
      const errorObj = new Error(error.message);
      errorObj.stack = error.extensions?.exception?.stacktrace?.join('\n');

      throw errorObj;
    }

    return data.data as T;
  }

  throw response;
}

export type { Variables };
export default performGQLQuery;
