import { test, expect } from '../fixtures/test';

test.describe('24. Table Column Sorting', () => {
  test('sorts list table by clicking sortable column headings', async ({ page }) => {
    await page.goto('/list/list-acacia-1');
    await expect(page).toHaveURL(/.*\/list\/list-acacia-1/);

    // Initial table records loaded - first record is Acacia dealbata
    const firstRowLink = page.locator('table tbody tr').first().locator('a').first();
    await expect(firstRowLink).toHaveText('Acacia dealbata');

    // Click "Scientific name" column sort button
    const scientificNameSort = page.locator('th').filter({ hasText: 'Scientific name' }).locator('button');
    await expect(scientificNameSort).toBeVisible();
    await scientificNameSort.click();

    // Verify sort or dir param is updated in URL
    await expect(page).toHaveURL(/(sort=|dir=)/);

    // Verify row re-ordering in the DOM (descending sort puts Emblema pictum first)
    await expect(firstRowLink).toHaveText('Emblema pictum');
  });
});
