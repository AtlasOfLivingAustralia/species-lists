import { useEffect, useState } from 'react';
import { useMounted } from '@mantine/hooks';

import performGQLQuery, { Variables } from './performGQLQuery';

interface QueryHookOptions {
  lazy?: boolean;
  clearDataOnUpdate?: boolean;
  token?: string;
}

function useGQLQuery<T>(
  query: string,
  initialVariables = {},
  options: QueryHookOptions = {
    clearDataOnUpdate: true,
  }
) {
  const [variables, setVariables] = useState<Variables>(initialVariables);
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const mounted = useMounted();

  useEffect(() => {
    async function runQuery() {
      if (data && options.clearDataOnUpdate) setData(null);
      console.log('Running query:', query, variables);
      try {
        const queryData = await performGQLQuery<T>(
          query,
          variables,
          options.token
        );
        console.log('Query result:', queryData);
        setData(queryData);
        setError(null);
      } catch (queryError) {
        setError(queryError as Error);
      }
    }

    if (!(options.lazy && !mounted)) runQuery();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [variables, options.lazy]);

  const update = (newVariables: Variables) => setVariables(newVariables);

  return { data, error, update };
}

export default useGQLQuery;
