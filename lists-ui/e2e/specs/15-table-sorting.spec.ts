import { test, expect } from '../fixtures/test';

test.describe('24. Table Column Sorting', () => {
  test('sorts list table by clicking sortable column headings', async ({ page }) => {
    await page.goto('/list/list-acacia-1');
    await expect(page).toHaveURL(/.*\/list\/list-acacia-1/);

    // Initial table records loaded
    await expect(page.getByRole('link', { name: 'Acacia dealbata' })).toBeVisible();

    // Find sort buttons in table header
    const sortButtons = page.locator('th button');
    const count = await sortButtons.count();
    expect(count).toBeGreaterThan(0);

    // Click first sort button
    await sortButtons.first().click();

    // Verify sort or dir param is updated in URL
    await expect(page).toHaveURL(/(sort=|dir=)/);
  });
});
