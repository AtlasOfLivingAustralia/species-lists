# base-branding's Bootstrap 5 output as the lists-ui theme

A portal built with [base-branding](https://github.com/living-atlases/base-branding) already
publishes `dist/bs5/` for its Bootstrap 5 apps: `banner.mustache`, `footer.mustache`,
`assets.mustache` and the `css/`, `js/` and `fonts/` they use. The templates follow the same
contract as lists-ui's (`{{containerClass}}`, `{{searchServer}}{{searchPath}}`, `{{loginStatus}}`,
`.loginBtn` / `.logoutBtn`), so lists-ui can point at them as they are, with no copy and no fork.
The only lists-ui file is `lists-bs5.css`, next to this README, which the community build copies to
`dist/theme/bs5/`.

## config.local.js

With `BRANDING` standing for where the portal publishes base-branding's `dist/` (for example
`https://branding.example.org/brand-2024`):

```js
const BRANDING = 'https://branding.example.org/brand-2024';
const BS5 = `${BRANDING}/bs5`;

window.APP_CONFIG_LOCAL = {
  VITE_THEME_HEADER_URL: `${BS5}/banner.mustache`,
  VITE_THEME_FOOTER_URL: `${BS5}/footer.mustache`,
  VITE_THEME_CONTAINER_CLASS: 'container-fluid',
  VITE_THEME_SEARCH_URL: 'https://species.example.org/search',

  // The stylesheets of assets.mustache, minus branding-bs5.css: before the app's CSS
  VITE_THEME_BASE_CSS_URL: [
    `${BS5}/css/bootstrap.min.css`,
    `${BS5}/css/ala-styles.min.css`,
    `${BS5}/css/autocomplete.min.css`,
    `${BS5}/css/autocomplete-extra.min.css`,
    `${BS5}/css/font-awesome.min.css`,
  ].join(','),
  // After the app's CSS: base-branding's own tweaks, the CSS the banner links (init.css), and
  // lists-bs5.css last
  VITE_THEME_CSS_URL: [
    `${BS5}/css/branding-bs5.css`,
    `${BRANDING}/css/init.css`,
    '/theme/bs5/lists-bs5.css',
  ].join(','),
  // The scripts of assets.mustache plus the banner's init.js (translations, cookie notice,
  // autocomplete URL), which must run before application.js reads its settings
  VITE_THEME_JS_URL: [
    `${BS5}/js/jquery.min.js`,
    `${BS5}/js/bootstrap.min.js`,
    `${BS5}/js/autocomplete.min.js`,
    `${BRANDING}/js/init.js`,
    `${BS5}/js/application.js`,
  ].join(','),
};
```

The branding host must answer lists-ui's `fetch` of the `.mustache` files with
`Access-Control-Allow-Origin` (the stylesheets and scripts are plain `<link>` / `<script>` and need
nothing). The `.htaccess` in `dist/bs5` only sets their content type.

`init.css` and `init.js` are the files `banner.mustache` links at its end. lists-ui sanitises the
template, so those tags do not load and have to be listed here.

## Why the two CSS keys and lists-bs5.css

Measured on the home page, against the same build without a theme:

- Loaded after the app's CSS, Bootstrap's reboot (`body` font size, line height and colour, ...)
  overrides the app's own element rules. `VITE_THEME_BASE_CSS_URL` inserts it before them instead,
  as atlas-index does with its base theme.
- ALA's Bootstrap 5 build sets a 14px root font size (`--bs-root-font-size`). Mantine and
  ala-mantine size everything in rem, so the whole app shrinks to 7/8. Load order cannot fix that,
  and Mantine's `scale` only reaches Mantine's own sizes, not ala-mantine's. `lists-bs5.css` puts
  the root back to 16px; the header is then sized as plain Bootstrap 5, a little larger than in the
  Grails apps.
- `ala-styles.css` makes every `<label>` bold with a bottom margin, which misaligns the filter
  checkboxes, and the reboot aligns every `<svg>` to the middle. `lists-bs5.css` resets both; its
  element rules lose to Mantine's classes, so they only reach what Mantine leaves at its default.

With these, every element of the content below the header had the same computed style as without
the theme, on the home page (809 elements) and on a list page (25,096), and so did the page-size
dropdown, which Mantine renders outside that content.

## What works and what does not

- Header, footer, translations (`data-i18n`) and the language menu, the "More" dropdown, the search
  form (to `VITE_THEME_SEARCH_URL`), and sign in / out, which go through lists-ui's OIDC client.
- The autocomplete runs: lists-ui keeps the banner's `#autoCompleteTemplate` and suggestions are
  fetched and rendered. `application.js` compiles that template into code (`tmpl()` uses
  `new Function`), so the banner needs the same trust as the scripts in `VITE_THEME_JS_URL`. But `banner.mustache` puts its container, `#autocompleteSearchALA`, in a
  Bootstrap `collapse` that nothing opens (in ALA's banner a search button toggles it), so the list
  stays hidden. That is in base-branding and affects its other Bootstrap 5 apps the same way.
- The footer's year is written by an inline `<script>`, which the sanitiser removes: it shows
  "© 2020-".
- base-branding's cookie notice (from `init.js`) shows as in its other apps.
