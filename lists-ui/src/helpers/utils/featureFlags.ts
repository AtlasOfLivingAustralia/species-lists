/**
 * Feature flag helper to determine whether active filter tags (pills)
 * are displayed above the results table on the lists and list pages.
 *
 * Configurable via:
 * 1. URL search param: `?showFilterTags=false` or `?showFilterTags=true`
 * 2. Environment variable: `VITE_SHOW_ACTIVE_FILTER_TAGS` ('true' | 'false')
 *
 * Defaults to true if not specified.
 */
export const showActiveFilterTags = (): boolean => {
  if (typeof window !== 'undefined') {
    const params = new URLSearchParams(window.location.search);
    if (params.has('showFilterTags')) {
      return params.get('showFilterTags') === 'true';
    }
  }
  return import.meta.env.VITE_SHOW_ACTIVE_FILTER_TAGS !== 'false';
};
