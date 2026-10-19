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
 *
 *     // --- Portal name ---------------------------------------------------------------------------
 *     VITE_APP_NAME: 'Example Species Lists',      // home page title (default 'ALA Species Lists')
 *     VITE_APP_TITLE_PREFIX: 'Example Lists',      // "<prefix> | Upload" (default 'ALA Lists')
 *     VITE_SUPPORT_EMAIL: 'support@example.org',   // default 'support@ala.org.au'
 *     VITE_FAVICON_URL: '/theme/my-portal/favicon.svg',
 *
 *     // --- Theme ---------------------------------------------------------------------------------
 *     // Header and footer from mustache templates, with the same contract as atlas-index, so one
 *     // banner.mustache / footer.mustache serves both apps. When set, they replace the ALA header,
 *     // footer and acknowledgement. Unset, nothing changes. See theme/example/README.md.
 *     VITE_THEME_HEADER_URL: '/theme/example/banner.mustache',
 *     VITE_THEME_FOOTER_URL: '/theme/example/footer.mustache',
 *     VITE_THEME_CONTAINER_CLASS: 'container-fluid',   // {{containerClass}} in the templates
 *     VITE_THEME_SEARCH_URL: 'https://species.example.org/search', // {{searchServer}}{{searchPath}}
 *     // Comma-separated, loaded in this order after the app's own CSS, so the last one wins.
 *     VITE_THEME_CSS_URL: '/theme/example/accent-override.css',
 *     // Comma-separated, loaded in order once the header is in the page.
 *     VITE_THEME_JS_URL: '',
 *     // A Mantine palette name (violet, teal, indigo... or ala-mantine's rust, flamingo...) or a
 *     // hex colour such as '#6b2fb3'. Anything else is ignored with a console warning.
 *     VITE_THEME_PRIMARY_COLOR: 'violet',
 *
 *     // --- Analytics -----------------------------------------------------------------------------
 *     // Fathom site id. Empty in the community build, so it sends no analytics unless set.
 *     VITE_APP_FATHOM_ID: '',
 *   };
 *
 * To make a new variable overridable there is nothing to do: read it as `import.meta.env.VITE_X`
 * (the literal form; computed access or destructuring fails the community build on purpose).
 */
window.APP_CONFIG = {};
