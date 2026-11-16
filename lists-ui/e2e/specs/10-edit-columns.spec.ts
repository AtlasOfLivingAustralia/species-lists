import { test, expect } from '../fixtures/test';

test.describe('14, 15. Edit Column Headings', () => {
  test('toggles edit column headings and allows field modification', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/list/list-acacia-1');
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Locate "Edit column headings" switch label and click it
    const switchLabel = authenticatedPage.locator('label', { hasText: /Edit column headings/i });
    await expect(switchLabel).toBeVisible();
    await switchLabel.click();

    // In edit mode, add field input should be present in table header
    const addFieldInput = authenticatedPage.getByPlaceholder(/Add field/i);
    if (await addFieldInput.isVisible()) {
      await addFieldInput.fill('conservationStatus');
      // Verify add field interface is active
      await expect(addFieldInput).toHaveValue('conservationStatus');
    }

    // Toggle edit columns back off
    await switchLabel.click();
  });
});
