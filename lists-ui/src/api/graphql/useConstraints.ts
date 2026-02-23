import { SpeciesListConstraints } from '#/api';
import { ALAContextProps } from '#/helpers/context/ALAContext';
import { useEffect, useState } from 'react';
import { useMounted } from '@mantine/hooks';

interface UseConstraintsResult {
  constraints: SpeciesListConstraints | null;
  loaded: boolean;
}

/**
 * Fetches and returns the species list constraints (list types, licences,
 * regions, tags, etc.) from the ALA REST API.
 *
 * Extracted so that any form or component that needs these values can call
 * this hook rather than duplicating the fetch logic.
 */
export function useConstraints(ala: ALAContextProps): UseConstraintsResult {
  const [constraints, setConstraints] = useState<SpeciesListConstraints | null>(null);
  const mounted = useMounted();

  useEffect(() => {
    if (!mounted) return;

    async function fetchConstraints() {
      try {
        setConstraints(await ala.rest.lists.constraints());
      } catch (error) {
        console.error('Error fetching constraints:', error);
      }
    }

    fetchConstraints();
  }, [mounted, ala.rest.lists]);

  return {
    constraints,
    loaded: Boolean(constraints),
  };
}