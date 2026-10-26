import { test, expect } from '../fixtures/test';

test.describe('21, 22. TaxonID List Creation and List Deletion', () => {
  test('verifies Taxon ID match status and deletes species list', async ({ authenticatedPage }) => {
    // Navigate to list
    await authenticatedPage.goto('/list/list-acacia-1');
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Verify Taxon ID match is indicated in table or classification
    await expect(authenticatedPage.getByText('Taxon ID match', { exact: true }).first()).toBeVisible();

    // Click "Delete list" (trash can) button
    const deleteBtn = authenticatedPage.getByRole('button', { name: /Delete list/i });
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Confirm deletion modal should appear
    await expect(authenticatedPage.getByText(/Confirm list deletion/i)).toBeVisible();

    const confirmBtn = authenticatedPage.getByRole('button', { name: 'Confirm', exact: true });
    await expect(confirmBtn).toBeVisible();
    await confirmBtn.click();

    // Should redirect back to home page after deletion
    await expect(authenticatedPage).toHaveURL(/\/(#.*|\?.*)?$/);
  });
});
