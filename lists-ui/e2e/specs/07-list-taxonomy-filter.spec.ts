import { test, expect } from '../fixtures/test';

test.describe('9. List Higher Taxonomy and Record Filtering', () => {
  test('filters list records by taxonomy order and clicks scientific name', async ({ page }) => {
    await page.goto('/list/list-acacia-1');
    await expect(page).toHaveURL(/.*\/list\/list-acacia-1/);

    // Records visible
    await expect(page.getByRole('link', { name: 'Acacia dealbata' })).toBeVisible();

    // Look for order facet filter (Fabales)
    const orderFilter = page.getByRole('checkbox', { name: /Fabales/i });
    if (await orderFilter.isVisible()) {
      await orderFilter.check();
      // Ensure records matching Fabales remain visible
      await expect(page.getByRole('link', { name: 'Acacia dealbata' })).toBeVisible();
    }

    // Click on a table row or scientific name to open details drawer
    const row = page.getByRole('row').filter({ hasText: 'Acacia dealbata' });
    await row.click();

    // Verify record drawer opens showing taxonomy
    const drawerContent = page.locator('.mantine-Drawer-content');
    await expect(drawerContent).toBeVisible();
    await expect(drawerContent.getByRole('heading', { name: 'Taxonomy' })).toBeVisible();
  });
});
