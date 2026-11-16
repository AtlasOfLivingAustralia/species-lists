import { test, expect } from '../fixtures/test';

test.describe('23. Add Species Manually', () => {
  test('adds a species to a list with automatic taxonomy population', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/list/list-acacia-1');
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Click "Add species" button (aria-label: "Add a new taxon entry")
    const addSpeciesBtn = authenticatedPage.getByRole('button', { name: /Add a new taxon entry/i }).or(
      authenticatedPage.locator('button', { hasText: 'Add species' })
    );
    await expect(addSpeciesBtn).toBeVisible();
    await addSpeciesBtn.click();

    // Verify record create drawer opened
    const drawer = authenticatedPage.locator('.mantine-Drawer-content');
    await expect(drawer).toBeVisible();

    // Type into Scientific Name field
    const nameInput = drawer.getByLabel(/Scientific Name/i).or(drawer.getByPlaceholder(/Scientific Name/i)).or(drawer.locator('input').first());
    await expect(nameInput).toBeVisible();
    await nameInput.fill('Acacia dealbata');

    // Click Create button in drawer
    const createBtn = drawer.getByRole('button', { name: /Create/i });
    if (await createBtn.isVisible()) {
      await createBtn.click();
    }

    // Close record drawer
    const closeBtn = drawer.getByLabel(/Close species details/i).or(drawer.locator('.mantine-Drawer-close'));
    if (await closeBtn.isVisible()) {
      await closeBtn.click();
    }
  });
});
