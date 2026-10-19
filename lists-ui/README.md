# Species Lists - `list-ui` React App

Due to strict CORS settings on the dev/test servers, it is easier to just run a local version of the backend. See the [README file](https://github.com/AtlasOfLivingAustralia/species-lists/blob/develop/lists-service/README.md) in the `lists-service` directory for instructions. 

## Running the `lists-ui` app

Ensure you have installed the following:
- Node version v18.12.1 or later (`brew install node`)
- Yarn version v1.22.10 or later (`brew install yarn`)

For local development, take a copy of the `config/.env.development` and rename it to `.env.development.local`. DO NOT commit sensitive values to `.env.development`, noting that `.env.development.local` is protected by `.git_ignore`.

```sh
cd config
cp .env.development .env.development.local
```

Use https://userdetails.test.ala.org.au/profile/applications to create an "application" using the `Public Client (Client-side Application)` option. Configure it to use a callback URL of `http://localhost:5173` and save its value in your `.env.development.local` copy. Ask someone about how have additional `scope` values added to your "application". See the `VITE_AUTH_SCOPE` entry for required scopes.

Edit the following values in `.env.development.local`:

```yaml
VITE_API_BASEURL=http://localhost:8080 # assuming running a local backend
VITE_AUTH_REDIRECT_URI=http://localhost:5173
VITE_AUTH_CLIENT_ID=<your_new_client_id> # save this in a password manager
```

Running the app

```bash
yarn install # only needed first time or if new npm packages have been added
yarn run dev
```

View the app at `http://localhost:5173/`

## Runtime configuration (community build)

By default `VITE_*` values are baked into the build, so each deployment has to edit `config/.env.*` and rebuild. For deployments that want to point a built app at their own backend and identity provider without rebuilding, there is a second build profile from the same code:

```bash
yarn build:community   # tsc && vite build --mode community
```

It is `config/.env.production` plus `config/.env.community` on top (which sets `VITE_RUNTIME_CONFIG_ENABLED=true` and empties `VITE_APP_FATHOM_ID`). The output keeps `<script src="/config.js">` and `config.local.js` in `index.html` and ships `community/config.js`:

- `config.js` holds the defaults and documents every key. It is replaced on every deploy.
- `config.local.js` belongs to the deployment and is never shipped or overwritten. It only needs the keys it changes, and its values win. It is optional; a 404 for it is harmless.

```js
// dist/config.local.js
window.APP_CONFIG_LOCAL = {
  VITE_API_BASEURL: 'https://lists-api.example.org',
  VITE_AUTH_AUTHORITY: 'https://auth.example.org/cas/oidc/.well-known',
  VITE_AUTH_CLIENT_ID: 'your-public-client-id',
};
```

Any `VITE_*` variable can be set this way: the community build rewrites every `import.meta.env.VITE_X` in the app's sources to `getAppConfigValue('VITE_X', import.meta.env.VITE_X)`, so there is no list of keys to maintain and nothing to do when adding a variable. Read variables as the literal `import.meta.env.VITE_X`; the sources are parsed, so the same text inside a string or a comment is left alone. Any other use of `import.meta.env` (computed access `import.meta.env[key]`, destructuring, spreading or passing it whole, `import.meta.env?.VITE_X`, or assigning to a key) fails the community build with a clear error, so a key cannot silently ignore `config.js`. The build copies `community/` to the output except `config.local.js`, which belongs to each deployment.

### Look & feel

The same mechanism covers the portal's branding. All of these are optional and empty or ALA's by default, so the ALA build looks as it always has:

| Key | What it does |
|---|---|
| `VITE_THEME_HEADER_URL`, `VITE_THEME_FOOTER_URL` | Mustache templates that replace the ala-mantine header, and the footer plus acknowledgement. Same contract as atlas-index (`{{containerClass}}`, `{{searchServer}}{{searchPath}}`, `.loginStatus`, `.loginBtn` / `.logoutBtn`), so one template serves both apps. Sanitised with DOMPurify, so `<script>` in a template does not run; only `<script type="text/html" id="...">` blocks are kept, as inert text, for theme scripts that read them as templates (ALA's autocomplete), which may compile them into code, so templates need the same trust as `VITE_THEME_JS_URL`. |
| `VITE_THEME_CONTAINER_CLASS` | `{{containerClass}}`, default `container-fluid`. |
| `VITE_THEME_SEARCH_URL` | `{{searchServer}}{{searchPath}}` and the ALA header's search button, default `https://bie.ala.org.au`. |
| `VITE_THEME_BASE_CSS_URL` | Comma-separated stylesheets inserted before the app's CSS, in order, as atlas-index does with its base theme. For a base such as Bootstrap: its element rules (`body`, `a`, `label`...) then lose to the app's. |
| `VITE_THEME_CSS_URL` | Comma-separated stylesheets added after the app's CSS, in order (the last one wins). |
| `VITE_THEME_JS_URL` | Comma-separated scripts, loaded in order once the header is in the page. |
| `VITE_THEME_PRIMARY_COLOR` | A Mantine palette name or a hex colour; default ala-mantine's `rust`. |
| `VITE_APP_NAME`, `VITE_APP_TITLE_PREFIX` | Page titles, default `ALA Species Lists` and `ALA Lists`. |
| `VITE_SUPPORT_EMAIL` | Contact shown on error pages, default `support@ala.org.au`. |
| `VITE_FAVICON_URL` | Page icon. |
| `VITE_APP_FATHOM_ID` | Fathom analytics; loaded only when set. Empty in the community build. |

`community/theme/example/` is a small theme to try this locally. Living Atlas portals that build their header and footer with [base-branding](https://github.com/living-atlases/base-branding) can use its Bootstrap 5 output (`dist/bs5`) as is; `community/theme/bs5/` explains how, with the stylesheet that adapts it to lists-ui. The ALA build does not use base-branding and is unaffected.

The default build (`yarn build:production` and the other modes) removes those tags, does not copy `community/` and does not rewrite anything, so it is unchanged. To try it with the dev server, run `yarn dev --mode community` and create `community/config.local.js` (git-ignored).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type aware lint rules:

- Configure the top-level `parserOptions` property like this:

```js
export default {
  // other rules...
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    project: ['./tsconfig.json', './tsconfig.node.json'],
    tsconfigRootDir: __dirname,
  },
}
```

- Replace `plugin:@typescript-eslint/recommended` to `plugin:@typescript-eslint/recommended-type-checked` or `plugin:@typescript-eslint/strict-type-checked`
- Optionally add `plugin:@typescript-eslint/stylistic-type-checked`
- Install [eslint-plugin-react](https://github.com/jsx-eslint/eslint-plugin-react) and add `plugin:react/recommended` & `plugin:react/jsx-runtime` to the `extends` list
