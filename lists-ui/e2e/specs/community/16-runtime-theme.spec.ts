import { cssVariable, EXAMPLE_THEME, expect, test } from '../../fixtures/community';

/**
 * The community build's runtime theming, end to end: a deployment's config.local.js replaces the
 * ALA header, footer, favicon, title, colours and stylesheet without rebuilding, and a community
 * build that configures nothing still looks exactly like the ALA one.
 *
 * These run against the dev server started with `--mode community` (see playwright.config.ts), the
 * only mode where index.html keeps the config.js tags and `import.meta.env.VITE_*` is read through
 * them. The unit tests in test/runtimeTheme.test.ts cover the helpers; this covers the app
 * actually starting with them.
 */
test.describe('25. Community build: runtime theme', () => {
  test('replaces the ALA chrome with the portal theme from config.local.js', async ({
    page,
    serveRuntimeConfig,
  }) => {
    await serveRuntimeConfig(EXAMPLE_THEME);
    await page.goto('/');

    // The portal's own header and footer, from its mustache templates
    const banner = page.locator('.example-theme-banner');
    await expect(banner).toBeVisible();
    await expect(banner.getByText('Example Atlas')).toBeVisible();
    await expect(page.locator('.example-theme-footer')).toBeVisible();

    // {{containerClass}} and {{searchServer}}{{searchPath}} filled in, like atlas-index does
    await expect(banner).toHaveClass(/container-fluid/);
    await expect(page.locator('.example-theme-search form')).toHaveAttribute(
      'action',
      'https://species.example.org/search'
    );

    // ALA's header, footer and acknowledgement are gone, not merely hidden
    await expect(page.getByText(/acknowledges the Traditional Owners/)).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Contact Us' })).toHaveCount(0);

    // The template's {{loginURL}} points nowhere, so the app turns the link into an in-page
    // button it handles itself, and marks the signed-out state for the theme's CSS
    const loginBtn = page.locator('.loginBtn').first();
    await expect(loginBtn).toHaveAttribute('href', '#');
    await expect(loginBtn).not.toHaveAttribute('role', 'button');
    await expect(page.locator('.loginStatus').first()).toHaveClass(/signedOut/);

    // Title and favicon
    await expect(page).toHaveTitle('Example Species Lists');
    const favicon = page.locator('link[rel="icon"]');
    await expect(favicon).toHaveAttribute('href', '/theme/example/favicon.svg');
    // The ALA icon is an SVG; a portal's may not be, so the type is dropped rather than kept
    await expect(favicon).not.toHaveAttribute('type', /.+/);

    // The theme stylesheet is not just in <head>: it loaded, and its accent reached the page
    await expect(page.locator('head link[rel="stylesheet"][href="/theme/example/accent-override.css"]')).toHaveCount(1);
    expect(await cssVariable(page, '--lists-accent-color')).toBe('#b197fc');

    // A hex VITE_THEME_PRIMARY_COLOR becomes the `portal` palette and the app's primary colour
    expect(await cssVariable(page, '--mantine-color-portal-6')).toBe('#6b2fb3');
    expect(await cssVariable(page, '--mantine-primary-color-filled')).toBe('#6b2fb3');

    // The app itself still works under the theme
    await expect(page.getByText('Acacia', { exact: false }).first()).toBeVisible();
  });

  test('keeps the ALA look when the deployment configures no theme', async ({ page }) => {
    // No config.local.js is served: a fresh community deployment, where the dev server and a real
    // deployment both answer 404 for it
    await page.goto('/');

    // The runtime config scripts are present in this build, which is what makes it the community
    // one: the default build strips them from index.html
    expect(await page.evaluate(() => typeof window.APP_CONFIG)).toBe('object');
    expect(await page.evaluate(() => typeof window.APP_CONFIG_LOCAL)).toBe('undefined');

    // ALA's header, footer and acknowledgement, unchanged
    await expect(page.getByRole('link', { name: 'Contact Us' }).first()).toBeVisible();
    await expect(page.getByText(/acknowledges the Traditional Owners/).first()).toBeVisible();
    await expect(page.locator('.example-theme-banner')).toHaveCount(0);
    await expect(page.locator('.example-theme-footer')).toHaveCount(0);

    // Nothing injected: no theme stylesheet, and the build's own title and favicon
    await expect(page).toHaveTitle('ALA Species Lists');
    await expect(page.locator('link[rel="icon"]')).toHaveAttribute('href', '/favicon.svg');
    await expect(page.locator('head link[rel="stylesheet"]')).toHaveCount(1);
    expect(await cssVariable(page, '--lists-accent-color')).toBe('');
    expect(await cssVariable(page, '--mantine-color-portal-6')).toBe('');

    await expect(page.getByText('Acacia', { exact: false }).first()).toBeVisible();
  });
});
