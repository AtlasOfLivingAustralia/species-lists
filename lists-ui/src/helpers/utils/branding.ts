/**
 * Portal name and contact, with ALA's as the defaults. Read in the literal
 * `import.meta.env.VITE_*` form so the community build can override each one from config.js /
 * config.local.js (see runtimeConfig.ts).
 */

/** Full portal name, used as the home page title. */
export function getAppName(): string {
  return import.meta.env.VITE_APP_NAME || 'ALA Species Lists';
}

/** Short name that prefixes every other page title, and the title before a page sets its own. */
export function getAppTitlePrefix(): string {
  return import.meta.env.VITE_APP_TITLE_PREFIX || 'ALA Lists';
}

/** Page title as "<prefix> | <page>". */
export function getPageTitle(page: string): string {
  return `${getAppTitlePrefix()} | ${page}`;
}

/** Address users are told to write to when something keeps failing. */
export function getSupportEmail(): string {
  return import.meta.env.VITE_SUPPORT_EMAIL || 'support@ala.org.au';
}
