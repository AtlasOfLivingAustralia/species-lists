import { test, expect } from '../fixtures/test';

test.describe('8. My Lists and Rematch Process', () => {
  test('navigates to My Lists and views owned lists', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/my-lists');
    await expect(authenticatedPage).toHaveURL(/.*\/my-lists/);

    // Verify page heading
    await expect(authenticatedPage.getByRole('heading', { name: 'My Lists' })).toBeVisible();

    // Verify owned lists are displayed
    await expect(authenticatedPage.getByText('Acacia characters testing')).toBeVisible();
  });

  test('triggers rematch list action from list view', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/list/list-acacia-1');
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Find rematch button
    const rematchBtn = authenticatedPage.getByRole('button', { name: /Rematch list/i });
    await expect(rematchBtn).toBeVisible();
    await rematchBtn.click();

    // Confirmation modal should open
    await expect(authenticatedPage.getByText(/Confirm list rematch/i)).toBeVisible();

    const confirmBtn = authenticatedPage.getByRole('button', { name: 'Confirm', exact: true });
    await expect(confirmBtn).toBeVisible();
    await confirmBtn.click();

    // Modal should close
    await expect(authenticatedPage.getByText(/Confirm list rematch/i)).not.toBeVisible();
  });
});
