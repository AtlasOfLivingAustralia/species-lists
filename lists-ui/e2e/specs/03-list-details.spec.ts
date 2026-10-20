import { test, expect } from '../fixtures/test';

test.describe('3. List Details and Summary Metrics', () => {
  test('displays list details and record count with distinct taxa', async ({ page }) => {
    await page.goto('/');

    // Click on the Acacia test list
    const listLink = page.getByRole('link', { name: 'Acacia characters testing' });
    await expect(listLink).toBeVisible();
    await listLink.click();

    await expect(page).toHaveURL(/.*\/list\/list-acacia-1/);

    // Verify list heading
    await expect(page.getByRole('heading', { name: 'Acacia characters testing' })).toBeVisible();

    // Verify summary line above records: "Showing 1-3 of 3 taxa (3 distinct taxa)"
    const summarySpan = page.locator('span').filter({ hasText: 'Showing' }).first();
    await expect(summarySpan).toBeVisible();
    await expect(summarySpan).toContainText('distinct taxa');

    // Verify table records are rendered
    await expect(page.getByRole('link', { name: 'Acacia dealbata' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Acacia pycnantha' })).toBeVisible();
  });
});
