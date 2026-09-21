import { Facet } from '#/api';

/**
 * Merges incoming facet counts with a base set of facets.
 * Ensures that options that drop to 0 count are retained with count = 0
 * rather than disappearing from the UI, providing a stable faceted search UX.
 *
 * @param baseFacets The facets available on the initial (unfiltered) search query
 * @param incomingFacets The latest facets returned from the server (filtered)
 * @returns Merged facets containing all known options with updated counts
 */
export function mergeFacetsWithBase(
  baseFacets: Facet[],
  incomingFacets: Facet[]
): Facet[] {
  if (!baseFacets || baseFacets.length === 0) {
    return incomingFacets || [];
  }
  if (!incomingFacets || incomingFacets.length === 0) {
    return baseFacets.map((bf) => ({
      ...bf,
      counts: (bf.counts || []).map((c) => ({ ...c, count: 0 })),
    }));
  }

  // Create lookup for incoming facets
  const incomingFacetMap = new Map<string, Facet>(
    incomingFacets.map((f) => [f.key, f])
  );

  const merged: Facet[] = baseFacets.map((baseFacet) => {
    const incoming = incomingFacetMap.get(baseFacet.key);
    const baseCounts = baseFacet.counts || [];
    if (!incoming) {
      return {
        ...baseFacet,
        counts: baseCounts.map((c) => ({ ...c, count: 0 })),
      };
    }

    const incomingCounts = incoming.counts || [];
    const incomingCountMap = new Map<string, number>(
      incomingCounts.map((c) => [c.value, c.count])
    );

    const mergedCounts = baseCounts.map((baseCount) => ({
      ...baseCount,
      count: incomingCountMap.get(baseCount.value) ?? 0,
    }));

    // Retain any incoming options that weren't in baseFacet
    const baseValueSet = new Set<string>(baseCounts.map((c) => c.value));
    for (const incCount of incomingCounts) {
      if (!baseValueSet.has(incCount.value)) {
        mergedCounts.push(incCount);
      }
    }

    return {
      ...incoming,
      counts: mergedCounts,
    };
  });

  // Retain any new facet keys in incoming that weren't in baseFacets
  const baseFacetKeySet = new Set<string>(baseFacets.map((f) => f.key));
  for (const incFacet of incomingFacets) {
    if (!baseFacetKeySet.has(incFacet.key)) {
      merged.push(incFacet);
    }
  }

  return merged;
}

export default mergeFacetsWithBase;
