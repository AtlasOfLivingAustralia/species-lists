/*
 * Default runtime configuration. Ships with the community build and is REPLACED on every deploy,
 * so do not edit it in a deployed site: put your own values in config.local.js next to it, which
 * is never shipped and never overwritten, and whose values win. That way a new release can update
 * these defaults without discarding what you set.
 *
 * Both files are plain scripts read before the app bundle, so a deployer changes settings after
 * the build instead of recompiling.
 *
 * The tags that load them are only present when the app is built with VITE_RUNTIME_CONFIG_ENABLED
 * (see config/.env.community and `yarn build:community`). The default build removes them and
 * ships neither file. config.local.js is optional; if it is not there the browser gets a 404 and
 * nothing breaks.
 *
 * Any VITE_* variable of the build can be set here: the community build rewrites every
 * `import.meta.env.VITE_X` in the app's sources so that a value declared in these files wins over
 * the one baked in at build time, and a blank value falls back to it. There is no list of keys to
 * maintain. A config.local.js looks like this, with only the keys it changes:
 *
 *   window.APP_CONFIG_LOCAL = {
 *     // Base URL of the lists-service API
 *     VITE_API_BASEURL: 'https://lists-api.example.org',
 *
 *     // OIDC provider, used by the browser to sign users in and out
 *     VITE_AUTH_AUTHORITY: 'https://auth.example.org/cas/oidc/.well-known',
 *     VITE_AUTH_CLIENT_ID: 'your-public-client-id',
 *     VITE_AUTH_REDIRECT_URI: 'https://lists.example.org',
 *     VITE_AUTH_SCOPE: 'openid profile email roles',
 *     VITE_AUTH_END_SESSION_URI: 'https://auth.example.org/cas/logout',
 *   };
 *
 * To make a new variable overridable there is nothing to do: read it as `import.meta.env.VITE_X`
 * (the literal form; computed access or destructuring fails the community build on purpose).
 */
window.APP_CONFIG = {};
