import { test, expect } from '../fixtures/test';

test.describe('13, 19. Edit List Metadata', () => {
  test('opens metadata modal, updates fields and toggles visibility', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/list/list-acacia-1');
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Click "Edit metadata" button
    const editMetadataBtn = authenticatedPage.getByRole('button', { name: /Edit metadata/i });
    await expect(editMetadataBtn).toBeVisible();
    await editMetadataBtn.click();

    // Verify modal is open
    await expect(authenticatedPage.getByText(/Edit List Metadata/i)).toBeVisible();

    // Edit description
    const descField = authenticatedPage.getByLabel(/Description/i);
    await expect(descField).toBeVisible();
    await descField.fill('Updated description from automated test');

    // Submit update
    const updateBtn = authenticatedPage.getByRole('button', { name: /Update/i });
    await expect(updateBtn).toBeVisible();
    await updateBtn.click();

    // Modal should close and success notification shown
    await expect(authenticatedPage.getByText(/Edit List Metadata/i)).not.toBeVisible();
    await expect(authenticatedPage.getByText(/updated successfully/i)).toBeVisible();
  });
});
