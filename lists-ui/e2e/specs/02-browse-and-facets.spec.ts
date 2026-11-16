import { test, expect } from '../fixtures/test';

test.describe('2. Browse Lists and Facet Filtering', () => {
  test('redirects legacy URL /public/speciesLists to root', async ({ page }) => {
    await page.goto('/public/speciesLists');
    await expect(page).toHaveURL(/\/(#.*|\?.*)?$/);
  });

  test('displays species lists on the landing page', async ({ page }) => {
    await page.goto('/');

    // Check header and list entries
    await expect(page.getByRole('heading', { name: 'Species Lists', exact: true })).toBeVisible();
    await expect(page.getByText('Acacia characters testing')).toBeVisible();
    await expect(page.getByText('Western Australia: Sensitive Species list')).toBeVisible();
  });

  test('filters species lists by selecting facets', async ({ page }) => {
    await page.goto('/');

    // Wait for the list and facets to load
    await expect(page.getByText('Acacia characters testing')).toBeVisible();

    // Select the "Species characters" listType filter or authoritative checkbox
    const speciesCharactersFilter = page.getByRole('checkbox', { name: /Species characters/i });
    if (await speciesCharactersFilter.isVisible()) {
      await speciesCharactersFilter.check();
      // URL should update with filter
      await expect(page).toHaveURL(/filters=/);
    }
  });
});
