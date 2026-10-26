import { Page } from '@playwright/test';

import { cssVariable } from '../fixtures/css';
import { expect, test } from '../fixtures/test';

/**
 * The ALA build looks and behaves as it did before the runtime theming: header, footer and
 * acknowledgement from ala-mantine, ALA's titles, favicon and colours, Fathom analytics, and the
 * header's search and login. Everything a portal can override is read through
 * `import.meta.env.VITE_*` with ALA's value as the default, so this pins those defaults.
 *
 * These run against the default dev server, the ALA build. The same expectations hold on the
 * code before the runtime theming was added.
 */

const FATHOM_SCRIPT = 'https://cdn.usefathom.com/script.js';

/** Answers Fathom with an empty script, so a test run never reports a page view to ALA's site. */
async function stubFathom(page: Page): Promise<void> {
  await page.route(`${FATHOM_SCRIPT}*`, (route) =>
    route.fulfill({ status: 200, contentType: 'text/javascript', body: '' })
  );
}

test.describe('26. ALA build: look and behaviour unchanged', () => {
  test('shows the ALA header, footer and acknowledgement', async ({ page }) => {
    await stubFathom(page);
    await page.goto('/');
    await expect(page.getByText('Acacia', { exact: false }).first()).toBeVisible();

    await expect(page.getByRole('button', { name: 'Search the Atlas of Living Australia' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Contact us' }).first()).toHaveAttribute(
      'href',
      'https://www.ala.org.au/contact-us/'
    );
    await expect(page.getByRole('button', { name: 'Login' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Copyright' })).toBeVisible();
    await expect(page.getByText(/The Atlas of Living Australia acknowledges the Traditional Owners/)).toBeVisible();
  });

  test('keeps the ALA title, favicon, stylesheets, colours and analytics', async ({ page }) => {
    await stubFathom(page);
    await page.goto('/');
    await expect(page.getByText('Acacia', { exact: false }).first()).toBeVisible();

    await expect(page).toHaveTitle('ALA Species Lists');
    const favicon = page.locator('link[rel="icon"]');
    await expect(favicon).toHaveCount(1);
    await expect(favicon).toHaveAttribute('href', '/favicon.svg');
    await expect(favicon).toHaveAttribute('type', 'image/svg+xml');

    // No runtime config: the ALA build strips the config.js tags from index.html
    await expect(page.locator('script[src$="config.js"], script[src$="config.local.js"]')).toHaveCount(0);
    expect(await page.evaluate(() => typeof (window as { APP_CONFIG?: unknown }).APP_CONFIG)).toBe('undefined');

    // The only stylesheet link is the Roboto font; no theme stylesheet is added
    const stylesheets = page.locator('head link[rel="stylesheet"]');
    await expect(stylesheets).toHaveCount(1);
    await expect(stylesheets).toHaveAttribute('href', /^https:\/\/fonts\.googleapis\.com\//);

    // ALA's rust is still the primary colour, so the buttons and breadcrumbs that now read the
    // primary colour (instead of naming rust) look the same, and no accent override is set
    const rust = await cssVariable(page, '--mantine-color-rust-filled');
    expect(rust).not.toBe('');
    expect(await cssVariable(page, '--mantine-primary-color-filled')).toBe(rust);
    expect(await cssVariable(page, '--mantine-primary-color-6')).toBe(
      await cssVariable(page, '--mantine-color-rust-6')
    );
    expect(await cssVariable(page, '--mantine-primary-color-filled-hover')).toBe(
      await cssVariable(page, '--mantine-color-rust-filled-hover')
    );
    expect(await cssVariable(page, '--lists-accent-color')).toBe('');

    // Fathom is loaded once, with the build's site id, as index.html used to load it
    const fathom = page.locator(`script[src="${FATHOM_SCRIPT}"]`);
    await expect(fathom).toHaveCount(1);
    await expect(fathom).toHaveAttribute('data-site', /^[A-Z0-9]+$/);
    await expect(fathom).toHaveAttribute('data-spa', 'auto');
    await expect(fathom).toHaveAttribute('defer', '');
  });

  test('keeps the ALA page titles', async ({ adminPage }) => {
    await stubFathom(adminPage);

    await adminPage.goto('/upload');
    await expect(adminPage).toHaveTitle('ALA Lists | Upload');

    await adminPage.goto('/admin');
    await expect(adminPage).toHaveTitle('ALA Lists | Admin');
  });

  test('sends the header search to the ALA species search', async ({ page }) => {
    await stubFathom(page);
    await page.route('https://bie.ala.org.au/**', (route) =>
      route.fulfill({ status: 200, contentType: 'text/html', body: '<title>BIE</title>' })
    );
    await page.goto('/');

    await page.getByRole('button', { name: 'Search the Atlas of Living Australia' }).click();
    await expect(page).toHaveURL(/^https:\/\/bie\.ala\.org\.au\//);
  });

  test('starts the OIDC sign-in from the header login button', async ({ page }) => {
    await stubFathom(page);
    // The identity provider is the build's VITE_AUTH_AUTHORITY; answer its discovery document so
    // the sign-in redirect goes to a host that only this test serves
    await page.route('**/.well-known/openid-configuration', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          issuer: new URL(route.request().url()).href.replace('/.well-known/openid-configuration', ''),
          authorization_endpoint: 'https://auth.example.test/authorize',
          token_endpoint: 'https://auth.example.test/token',
          end_session_endpoint: 'https://auth.example.test/logout',
          jwks_uri: 'https://auth.example.test/jwks',
        }),
      })
    );
    await page.route('https://auth.example.test/**', (route) =>
      route.fulfill({ status: 200, contentType: 'text/html', body: '<title>Sign in</title>' })
    );
    await page.goto('/');

    await page.getByRole('button', { name: 'Login' }).click();
    await expect(page).toHaveURL(/^https:\/\/auth\.example\.test\/authorize\?.*client_id=/);
  });
});
