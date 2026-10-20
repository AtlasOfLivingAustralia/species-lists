import { test, expect } from '../fixtures/test';

test.describe('1. Version and Admin Access', () => {
  test('displays frontend app version in admin panel for admin user', async ({ adminPage }) => {
    await adminPage.goto('/admin');
    await expect(adminPage).toHaveURL(/.*\/admin/);

    // Header in admin page includes "vX.Y.Z"
    const versionBadge = adminPage.getByText(/v\d+\.\d+\.\d+/);
    await expect(versionBadge).toBeVisible();
  });

  test('blocks non-admin users from accessing admin route', async ({ page }) => {
    await page.goto('/admin');
    // Unauthenticated user should not be allowed into admin panel and redirected to /
    await expect(page).toHaveURL(/\/(#.*|\?.*)?$/);
    await expect(page.getByText('Login required')).toBeVisible();
  });

  test('blocks non-admin authenticated users with access denied', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/admin');
    // Authenticated regular user should see access denied and be redirected to /
    await expect(authenticatedPage).toHaveURL(/\/(#.*|\?.*)?$/);
    await expect(authenticatedPage.getByText('Access denied')).toBeVisible();
  });
});
