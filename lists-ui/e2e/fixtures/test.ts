import { test as baseTest, expect, Page } from '@playwright/test';
import { createMockOidcUser, injectAuthSession } from './auth';
import { setupMockApi, MockApiOptions } from './mockApi';

export interface TestFixtures {
  setupMocks: (options?: MockApiOptions) => Promise<void>;
  authenticatedPage: Page;
  adminPage: Page;
}

export const test = baseTest.extend<TestFixtures>({
  page: async ({ page }, use) => {
    // Set up mock API by default for any test page
    await setupMockApi(page);
    await use(page);
  },
  setupMocks: async ({ page }, use) => {
    await use(async (options?: MockApiOptions) => {
      await setupMockApi(page, options);
    });
  },
  authenticatedPage: async ({ page }, use) => {
    await injectAuthSession(page, createMockOidcUser({ roles: ['user'] }));
    await setupMockApi(page);
    await use(page);
  },
  adminPage: async ({ page }, use) => {
    await injectAuthSession(page, createMockOidcUser({ roles: ['admin'] }));
    await setupMockApi(page);
    await use(page);
  },
});

export { expect };
