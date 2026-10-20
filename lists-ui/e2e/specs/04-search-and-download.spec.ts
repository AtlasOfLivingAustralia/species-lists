import { test, expect } from '../fixtures/test';

test.describe('4. Search and Download List', () => {
  test('searches for a list and downloads CSV file', async ({ page }) => {
    await page.goto('/');

    // Type "acacia" into search input
    const searchInput = page.getByPlaceholder(/Search lists by name/i);
    await searchInput.fill('acacia');

    const searchButton = page.getByRole('button', { name: 'Search', exact: true });
    await searchButton.click();

    // Verify search result
    const searchResult = page.getByRole('link', { name: 'Acacia characters testing' });
    await expect(searchResult).toBeVisible();

    // Navigate to list
    await searchResult.click();
    await expect(page).toHaveURL(/.*\/list\/list-acacia-1/);

    // Setup download listener
    const downloadPromise = page.waitForEvent('download');

    // Click "Download list" button
    const downloadBtn = page.getByRole('button', { name: /Download list/i });
    await expect(downloadBtn).toBeVisible();
    await downloadBtn.click();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('.csv');
  });
});
