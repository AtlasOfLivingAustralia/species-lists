import { test as baseTest, expect } from '@playwright/test';

import { setupMockApi } from './mockApi';

export { cssVariable } from './css';

/**
 * Fixtures for the community build, which is the same code as the ALA build with
 * VITE_RUNTIME_CONFIG_ENABLED on (see config/.env.community and viteRuntimeConfigPlugin.ts). In
 * that build index.html keeps <script src="/config.js"> and config.local.js, and every
 * `import.meta.env.VITE_*` is read through them, so a deployment changes settings after the build.
 *
 * config.js ships with the build (community/config.js, served by the dev server). config.local.js
 * belongs to the deployment, is never shipped, and is a 404 until someone adds it, so a test
 * serves its own to act as a deployment that has configured itself.
 */
export type RuntimeConfig = Record<string, string>;

declare global {
  // Declared for the app in src/helpers/utils/runtimeConfig.ts, which this project does not compile
  interface Window {
    APP_CONFIG?: Record<string, string | undefined>;
    APP_CONFIG_LOCAL?: Record<string, string | undefined>;
  }
}

/**
 * The example theme that ships in community/theme/example, pointed at by the URLs a deployment
 * would set in config.local.js.
 */
export const EXAMPLE_THEME: RuntimeConfig = {
  // Nothing should reach a real backend: the mock API answers these, and an unmatched request
  // goes to a host that does not exist rather than to ALA's servers.
  VITE_API_BASEURL: 'https://lists-api.example.org',
  VITE_APP_NAME: 'Example Species Lists',
  VITE_APP_TITLE_PREFIX: 'Example Lists',
  VITE_SUPPORT_EMAIL: 'support@example.org',
  VITE_FAVICON_URL: '/theme/example/favicon.svg',
  VITE_THEME_HEADER_URL: '/theme/example/banner.mustache',
  VITE_THEME_FOOTER_URL: '/theme/example/footer.mustache',
  VITE_THEME_CONTAINER_CLASS: 'container-fluid',
  VITE_THEME_SEARCH_URL: 'https://species.example.org/search',
  VITE_THEME_CSS_URL: '/theme/example/accent-override.css',
  // A hex colour, so it becomes the generated `portal` palette rather than a named Mantine one
  VITE_THEME_PRIMARY_COLOR: '#6b2fb3',
};

export interface CommunityFixtures {
  /** Serves a config.local.js holding `config`; call it before the first navigation. */
  serveRuntimeConfig: (config: RuntimeConfig) => Promise<void>;
}

export const test = baseTest.extend<CommunityFixtures>({
  page: async ({ page }, use) => {
    await setupMockApi(page);
    await use(page);
  },
  serveRuntimeConfig: async ({ page }, use) => {
    await use(async (config: RuntimeConfig) => {
      await page.route('**/config.local.js', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'text/javascript',
          body: `window.APP_CONFIG_LOCAL = ${JSON.stringify(config)};`,
        });
      });
    });
  },
});

export { expect };
