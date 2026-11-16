import { test as baseTest, expect, Page } from '@playwright/test';

export interface MockUserOptions {
  username?: string;
  email?: string;
  name?: string;
  roles?: string[];
}

export function createMockOidcUser(options: MockUserOptions = {}) {
  const username = options.username ?? 'test.user@ala.org.au';
  const email = options.email ?? 'test.user@ala.org.au';
  const name = options.name ?? 'Test User';
  const roles = options.roles ?? [];

  return {
    id_token: 'mock-id-token',
    access_token: 'mock-access-token',
    token_type: 'Bearer',
    scope: 'openid profile email ala/attrs ala/roles ala/internal users/read',
    profile: {
      sub: 'mock-sub-12345',
      'cognito:username': username,
      'cognito:groups': roles,
      email,
      name,
      given_name: 'Test',
      family_name: 'User',
      preferred_username: username,
    },
    expires_at: Math.floor(Date.now() / 1000) + 86400 * 365,
  };
}

export async function injectAuthSession(page: Page, user = createMockOidcUser()) {
  await page.addInitScript((mockUser) => {
    const authority = 'https://cognito-idp.ap-southeast-2.amazonaws.com/ap-southeast-2_OOXU9GW39';
    const clientIds = [
      'q320ak4mvrdhs73u62v49039s',
      '2e2gl2gbl5o6shjb8nqlr39e8k',
      '46gj0tm07vt0pv8m8o21tmcl60',
      '52820hflhhnfl7v4c56t9e16d4',
    ];
    for (const clientId of clientIds) {
      window.sessionStorage.setItem(`oidc.user:${authority}:${clientId}`, JSON.stringify(mockUser));
    }
  }, user);
}

export const test = baseTest.extend<{
  authenticatedPage: Page;
  adminPage: Page;
}>({
  authenticatedPage: async ({ page }, use) => {
    await injectAuthSession(page, createMockOidcUser({ roles: ['user'] }));
    await use(page);
  },
  adminPage: async ({ page }, use) => {
    await injectAuthSession(page, createMockOidcUser({ roles: ['admin'] }));
    await use(page);
  },
});

export { expect };
