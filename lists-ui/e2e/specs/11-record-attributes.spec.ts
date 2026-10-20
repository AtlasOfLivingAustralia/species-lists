import { test, expect } from '../fixtures/test';

test.describe('16, 17. Record Attributes and Removal', () => {
  test('opens record drawer, views supplied values, and edits attributes', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/list/list-acacia-1');
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Click on row to open drawer
    const row = authenticatedPage.getByRole('row').filter({ hasText: 'Acacia dealbata' });
    await row.click();

    // Verify drawer opened
    await expect(authenticatedPage.getByRole('heading', { name: 'Taxonomy' })).toBeVisible();

    // Toggle "Show supplied values"
    const showSuppliedLabel = authenticatedPage.locator('label', { hasText: /Show supplied values/i });
    await expect(showSuppliedLabel).toBeVisible();
    await showSuppliedLabel.click();

    // Toggle "Edit" switch
    const editLabel = authenticatedPage.locator('label', { hasText: /^Edit$/i });
    await expect(editLabel).toBeVisible();
    await editLabel.click();

    // Verify "Update" button is visible
    const updateButton = authenticatedPage.getByRole('button', { name: 'Update', exact: true });
    await expect(updateButton).toBeVisible();

    // Verify "Remove from list" button is visible
    const removeButton = authenticatedPage.getByRole('button', { name: /Remove from list/i });
    await expect(removeButton).toBeVisible();
  });

  test('triggers remove from list confirmation modal', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/list/list-acacia-1');
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Open record drawer
    const row = authenticatedPage.getByRole('row').filter({ hasText: 'Acacia dealbata' });
    await row.click();

    // Enter edit mode
    const editLabel = authenticatedPage.locator('label', { hasText: /^Edit$/i });
    await editLabel.click();

    // Click "Remove from list"
    const removeButton = authenticatedPage.getByRole('button', { name: /Remove from list/i });
    await removeButton.click();

    // Confirm deletion modal should appear
    await expect(authenticatedPage.getByText(/Confirm item deletion/i)).toBeVisible();

    const cancelBtn = authenticatedPage.locator('.mantine-Modal-content').getByRole('button', { name: 'Cancel' });
    await cancelBtn.click();
    await expect(authenticatedPage.getByText(/Confirm item deletion/i)).not.toBeVisible();
  });
});
