import path from 'path';
import { fileURLToPath } from 'url';
import { test, expect } from '../fixtures/test';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test.describe('5, 6, 7. Upload List Workflows', () => {
  test('uploads CSV species list with metadata and creates list', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/upload');
    await expect(authenticatedPage).toHaveURL(/.*\/upload/);

    // Verify Dropzone is visible
    const fileInput = authenticatedPage.locator('input[type="file"]');
    const testFilePath = path.join(__dirname, '../fixtures/testFiles/Acacia_characters-testing.csv');

    // Attach test CSV file
    await fileInput.setInputFiles(testFilePath);

    // After upload, metadata form should appear
    const titleInput = authenticatedPage.locator('input[name="title"]');
    await expect(titleInput).toBeVisible();

    // Fill metadata
    await titleInput.fill('Acacia characters testing');

    const descInput = authenticatedPage.locator('textarea[name="description"]');
    if (await descInput.isVisible()) {
      await descInput.fill('Test list of Acacia characters uploaded via Playwright');
    }

    // Select List Type (required)
    const listTypeInput = authenticatedPage.getByPlaceholder(/List type/i);
    await listTypeInput.click();
    await authenticatedPage.getByRole('option', { name: /Species characters/i }).click();

    // Select Licence
    const licenceInput = authenticatedPage.getByPlaceholder(/Select a licence/i);
    if (await licenceInput.isVisible()) {
      await licenceInput.click();
      const option = authenticatedPage.getByRole('option', { name: /CC0/i });
      if (await option.isVisible()) {
        await option.click();
      }
    }

    // Click "Create" button
    const createButton = authenticatedPage.getByRole('button', { name: /Create/i });
    await expect(createButton).toBeVisible();
    await createButton.click();

    // Should transition to ingestion progress or list view
    await expect(authenticatedPage).toHaveURL(/.*\/list\/list-acacia-1/);
  });
});
