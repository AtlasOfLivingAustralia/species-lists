/**
 * Settings a deployment can change after the build, read from two globals set by two scripts
 * loaded before the app bundle:
 *
 *   config.js        ships with the build and is replaced on every deploy. It holds the defaults
 *                    and documents how to use the file (see community/config.js).
 *   config.local.js  belongs to the deployment, is never shipped and never overwritten. Its
 *                    values win over the defaults. It is optional; a 404 for it is harmless.
 *
 * Nothing calls getAppConfigValue by hand. In the community build, viteRuntimeConfigPlugin rewrites
 * every `import.meta.env.VITE_X` in the app's sources to
 * `getAppConfigValue('VITE_X', import.meta.env.VITE_X)`, so any `VITE_*` variable can be overridden
 * from config.js / config.local.js, with no list of keys to maintain. The default build is not
 * rewritten and does not load these files, so it is unchanged.
 */
export type RuntimeConfig = Record<string, string | undefined>;

declare global {
  interface Window {
    /** Defaults, from the config.js that ships with the build. */
    APP_CONFIG?: RuntimeConfig;
    /** Overrides, from the deployment's own config.local.js. */
    APP_CONFIG_LOCAL?: RuntimeConfig;
  }
}

export function getRuntimeConfig(): RuntimeConfig {
  if (typeof window === 'undefined') return {};
  return { ...window.APP_CONFIG, ...window.APP_CONFIG_LOCAL };
}

/**
 * The value config.js / config.local.js declares for `key`, or `fallback` (the build's own
 * `import.meta.env.VITE_*`) when the deployment has not set it or left it blank.
 */
export function getAppConfigValue(key: string, fallback: string): string {
  const value = getRuntimeConfig()[key];
  return typeof value === 'string' && value.trim() ? value : fallback;
}
