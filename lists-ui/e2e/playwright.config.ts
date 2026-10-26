import { defineConfig, devices } from '@playwright/test';

const PORT = 5173;
const baseURL = process.env.BASE_URL || `http://localhost:${PORT}`;

/**
 * The community build (`--mode community`) needs a server of its own: it is the only mode where
 * index.html keeps the config.js tags and `import.meta.env.VITE_*` is read through them, so the
 * specs under specs/community run against this one. Set COMMUNITY_BASE_URL to use a deployed
 * community build instead; with BASE_URL set and no COMMUNITY_BASE_URL, they are skipped, since a
 * deployed ALA build cannot answer them.
 */
const COMMUNITY_PORT = 5174;
const communityBaseURL = process.env.COMMUNITY_BASE_URL || `http://localhost:${COMMUNITY_PORT}`;
const runCommunity = !process.env.BASE_URL || !!process.env.COMMUNITY_BASE_URL;

export default defineConfig({
  testDir: './specs',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['list'],
    ['html', { outputFolder: './playwright-report', open: 'never' }],
  ],
  outputDir: './test-results',
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ignoreHTTPSErrors: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: /specs[\\/]community[\\/]/,
    },
    ...(runCommunity
      ? [
          {
            name: 'community',
            use: { ...devices['Desktop Chrome'], baseURL: communityBaseURL },
            testMatch: /specs[\\/]community[\\/].*\.spec\.ts$/,
          },
        ]
      : []),
  ],
  webServer: [
    ...(process.env.BASE_URL
      ? []
      : [
          {
            command: `yarn dev --port ${PORT}`,
            port: PORT,
            reuseExistingServer: !process.env.CI,
            timeout: 120_000,
          },
        ]),
    ...(runCommunity && !process.env.COMMUNITY_BASE_URL
      ? [
          {
            command: `yarn dev --mode community --port ${COMMUNITY_PORT}`,
            port: COMMUNITY_PORT,
            reuseExistingServer: !process.env.CI,
            timeout: 120_000,
          },
        ]
      : []),
  ],
});
