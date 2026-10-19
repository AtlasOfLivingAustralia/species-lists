# Example theme

A small theme to see runtime theming work locally, adapted from atlas-index's
`search-ui/community/theme/example`. It is not ALA's header and footer, just a minimal banner and
footer that use the same contract as atlas-index's `Header` / `Footer`: `{{containerClass}}`,
`{{searchServer}}{{searchPath}}`, `{{loginStatus}}` and the `loginBtn` / `logoutBtn` classes (see
`src/components/ExternalChrome.tsx`). A portal that already has templates for atlas-index can point
lists-ui at the same files.

`community/` is served at the site root by the dev server and copied into `dist/` by
`yarn build:community`, so the same paths work in both. In `community/config.local.js` (git-ignored)
or the deployment's `config.local.js`:

```js
window.APP_CONFIG_LOCAL = {
  VITE_APP_NAME: 'Example Species Lists',
  VITE_APP_TITLE_PREFIX: 'Example Lists',
  VITE_THEME_HEADER_URL: '/theme/example/banner.mustache',
  VITE_THEME_FOOTER_URL: '/theme/example/footer.mustache',
  VITE_THEME_CSS_URL: '/theme/example/accent-override.css',
  VITE_THEME_SEARCH_URL: 'https://species.example.org/search',
  VITE_THEME_PRIMARY_COLOR: 'violet',
};
```

Then `yarn dev --mode community`, or `yarn build:community` and serve `dist/`.

## What changes and what does not

- The header, footer and acknowledgement switch to these templates.
- The app's primary colour (buttons, links, active states) follows `VITE_THEME_PRIMARY_COLOR`: a
  Mantine palette name such as `violet`, or a hex colour. Mantine writes its colour variables from
  the app after the theme CSS loads, so a `:root { --mantine-primary-color-... }` in a stylesheet would
  lose; use the key.
- The secondary accent is `--lists-accent-color`, which only the theme declares (here in
  `accent-override.css`).
- Layout, icons and the rest of the styling stay as built.

## Differences with atlas-index

- Templates are sanitised with DOMPurify, so `<script>` blocks are removed and do not run. The one
  exception is `<script type="text/html" id="...">`, which the browser never runs: lists-ui re-adds
  it as inert text, because theme scripts read it as a template (the banner's
  `#autoCompleteTemplate` for ALA's autocomplete). The browser never runs it, but a theme script may
  compile it into code (ALA's `tmpl()` does), so serve templates with the same trust as
  `VITE_THEME_JS_URL`. An inline script that writes into the page, such
  as a footer's copyright year, does not run.
- The keys carry the `VITE_` prefix (`VITE_THEME_HEADER_URL` rather than `THEME_HEADER_URL`), which the
  community build needs to make them overridable.
- `VITE_THEME_CSS_URL` loads after the app's CSS, for overrides such as this example's.
  `VITE_THEME_BASE_CSS_URL` loads before it, as atlas-index does with its base theme, for a base such
  as Bootstrap. See `../bs5/README.md` to use base-branding's Bootstrap 5 output.
