// Example runtime configuration for lists-ui.
//
// Copy this file to `config.js` in the same directory (served at `/config.js`)
// to point a deployment at its own backend and OIDC provider without rebuilding
// the app. If `/config.js` is not present, the app falls back to the VITE_*
// values baked in at build time (ALA's current default behaviour).
window.__APP_CONFIG__ = {
  VITE_API_BASEURL: 'https://your-backend.example.org',
  VITE_AUTH_AUTHORITY: 'https://your-idp.example.org/.well-known',
  VITE_AUTH_CLIENT_ID: 'your-oidc-client-id',
  VITE_AUTH_REDIRECT_URI: 'https://your-frontend.example.org',
  VITE_AUTH_SCOPE: 'openid profile email',
  VITE_AUTH_END_SESSION_URI: 'https://your-idp.example.org/logout',
};
