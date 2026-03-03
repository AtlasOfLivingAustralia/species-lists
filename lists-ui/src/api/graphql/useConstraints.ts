import { SpeciesListConstraints } from '#/api';
import { ALAContextProps } from '#/helpers/context/ALAContext';
import { useEffect, useState } from 'react';
import { useMounted } from '@mantine/hooks';

interface UseConstraintsResult {
  constraints: SpeciesListConstraints | null;
  loaded: boolean;
}

let constraintsCache: SpeciesListConstraints | null = null;
let constraintsPromise: Promise<SpeciesListConstraints> | null = null;

/**
 * Fetches and returns the species list constraints (list types, licences,
 * regions, tags, etc.) from the ALA REST API. Values are cached in-memory 
 * to avoid redundant API calls across the app.
 *
 * Extracted so that any form or component that needs these values can call
 * this hook rather than duplicating the fetch logic.
 */
export function useConstraints(ala: ALAContextProps): UseConstraintsResult {
  const [constraints, setConstraints] = useState<SpeciesListConstraints | null>(constraintsCache);
  const mounted = useMounted();

  useEffect(() => {
    if (!mounted || constraintsCache) return;

    if (!constraintsPromise) {
      constraintsPromise = ala.rest.lists.constraints().then((data) => {
        constraintsCache = data;
        return data;
      });
    }

    let active = true;
    constraintsPromise
      .then((data) => {
        if (active) setConstraints(data);
      })
      .catch((error) => {
        console.error('Error fetching constraints:', error);
      });

    return () => {
      active = false;
    };
  }, [mounted, ala.rest.lists]);

  return {
    constraints,
    loaded: Boolean(constraints),
  };
}
