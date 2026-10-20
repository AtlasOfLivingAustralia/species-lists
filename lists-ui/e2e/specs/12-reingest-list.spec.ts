import path from 'path';
import { fileURLToPath } from 'url';
import { test, expect } from '../fixtures/test';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test.describe('20. Reingest Species List', () => {
  test('uploads replacement CSV and confirms re-ingestion', async ({ adminPage }) => {
    await adminPage.goto('/list/list-acacia-1');
    await expect(adminPage).toHaveURL(/.*\/list\/list-acacia-1/);

    // Click "Reingest list" button
    const reingestBtn = adminPage.getByRole('button', { name: /Reingest list/i });
    await expect(reingestBtn).toBeVisible();
    await reingestBtn.click();

    await expect(adminPage).toHaveURL(/.*\/list\/list-acacia-1\/reingest/);

    // Upload test CSV file
    const fileInput = adminPage.locator('input[type="file"]');
    const testFilePath = path.join(__dirname, '../fixtures/testFiles/Acacia_characters-testing.csv');
    await fileInput.setInputFiles(testFilePath);

    // Confirm reingestion button appears
    const confirmReingestBtn = adminPage.getByRole('button', { name: /Confirm re-ingestion/i });
    await expect(confirmReingestBtn).toBeVisible();
    await confirmReingestBtn.click();
  });
});
