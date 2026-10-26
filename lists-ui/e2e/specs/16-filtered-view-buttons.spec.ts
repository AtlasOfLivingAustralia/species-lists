import { test, expect } from '../fixtures/test';
import type { Page, Route } from '@playwright/test';

const TEST_QID = 'test-qid-123';
const FILTERED_URL = '/list/list-acacia-1?filters=classification.kingdom:Plantae';
const UNFILTERED_URL = '/list/list-acacia-1';

// The page keeps a "Species details" drawer mounted (also role=dialog), so the
// confirm modal is located by Mantine's data-modal-content marker instead.
const modal = (page: Page) => page.locator('section[data-modal-content]');
// Mantine confirm modals render [cancel, confirm] in that DOM order
const confirmButton = (page: Page) => modal(page).getByRole('button').last();
const cancelButton = (page: Page) => modal(page).getByRole('button').first();
// Scope radios are selected by value to stay independent of the UI locale
const scopeRadios = (page: Page) => modal(page).locator('input[type="radio"]');

/**
 * Stubs window.open so popups are captured instead of navigating, and mocks the
 * speciesListQid endpoint, recording every request URL it receives.
 */
async function stubExternalLinks(page: Page) {
  const qidRequests: string[] = [];

  await page.addInitScript(() => {
    (window as unknown as { __openedUrls: string[] }).__openedUrls = [];
    window.open = (url?: string | URL) => {
      (window as unknown as { __openedUrls: string[] }).__openedUrls.push(String(url));
      return null;
    };
  });

  await page.route('**/speciesListQid/**', async (route: Route) => {
    qidRequests.push(route.request().url());
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ qid: TEST_QID }),
    });
  });

  return {
    qidRequests,
    openedUrls: () => page.evaluate(() => (window as unknown as { __openedUrls: string[] }).__openedUrls),
  };
}

test.describe('16. Filtered view buttons (occurrence records / spatial portal)', () => {
  test('occurrence records modal is unchanged when no filters are active', async ({ page }) => {
    const { qidRequests, openedUrls } = await stubExternalLinks(page);

    await page.goto(UNFILTERED_URL);
    const button = page.getByRole('button', { name: /View occurrence records/i });
    await expect(button).toBeVisible();
    await button.click();

    await expect(modal(page)).toBeVisible();
    // The title key is not translated, so the English default is stable
    await expect(modal(page).getByText(/View Occurrence Records/i)).toBeVisible();

    // No scope choice when there are no filters
    await expect(scopeRadios(page)).toHaveCount(0);
    await confirmButton(page).click();

    // No QID is needed for an authoritative public list - direct species list search
    await expect.poll(() => qidRequests.length, { timeout: 10_000 }).toBe(0);
    await expect.poll(openedUrls).toHaveLength(1);
    const urls = await openedUrls();
    expect(urls[0]).toContain('species_list_uid:dr1001');
  });

  test('occurrence records modal offers a scope choice when filters are active', async ({ page }) => {
    const { qidRequests, openedUrls } = await stubExternalLinks(page);

    await page.goto(FILTERED_URL);
    const button = page.getByRole('button', { name: /View occurrence records/i });
    await expect(button).toBeVisible();
    await button.click();

    await expect(modal(page)).toBeVisible();
    await expect(scopeRadios(page)).toHaveCount(2);

    // Default choice is the filtered species; confirm fetches a filtered QID
    await confirmButton(page).click();

    await expect.poll(() => qidRequests.length, { timeout: 10_000 }).toBe(1);
    expect(qidRequests[0]).toContain('filters=classification.kingdom');
    expect(qidRequests[0]).toContain('Plantae');

    await expect.poll(openedUrls).toHaveLength(1);
    expect((await openedUrls())[0]).toContain('q=qid:test-qid-123');

    // Re-open and choose all species instead: the direct URL is used, no QID call
    await button.click();
    await expect(modal(page)).toBeVisible();
    await modal(page).locator('input[type="radio"][value="all"]').check();
    await confirmButton(page).click();

    await expect.poll(openedUrls).toHaveLength(2);
    expect((await openedUrls())[1]).toContain('species_list_uid:dr1001');
    expect(qidRequests.length).toBe(1);
  });

  test('spatial portal opens directly when no filters are active', async ({ page }) => {
    const { qidRequests, openedUrls } = await stubExternalLinks(page);

    await page.goto(UNFILTERED_URL);
    const button = page.getByRole('button', { name: /View in spatial portal/i });
    await expect(button).toBeVisible();
    await button.click();

    await expect.poll(() => qidRequests.length, { timeout: 10_000 }).toBe(1);
    expect(qidRequests[0]).not.toContain('filters=');

    // No modal is shown
    await expect(modal(page)).toHaveCount(0);

    await expect.poll(openedUrls).toHaveLength(1);
    const urls = await openedUrls();
    expect(urls[0]).toContain('q=qid:test-qid-123');
  });

  test('spatial portal asks for a scope choice when filters are active', async ({ page }) => {
    const { qidRequests, openedUrls } = await stubExternalLinks(page);

    await page.goto(FILTERED_URL);
    const button = page.getByRole('button', { name: /View in spatial portal/i });
    await expect(button).toBeVisible();
    await button.click();

    await expect(modal(page)).toBeVisible();
    await expect(scopeRadios(page)).toHaveCount(2);

    // Default (filtered) choice passes the active filters to the QID call
    await confirmButton(page).click();

    await expect.poll(() => qidRequests.length, { timeout: 10_000 }).toBe(1);
    expect(qidRequests[0]).toContain('filters=classification.kingdom');
    expect(qidRequests[0]).toContain('Plantae');

    await expect.poll(openedUrls).toHaveLength(1);
    expect((await openedUrls())[0]).toContain('q=qid:test-qid-123');
  });

  test('cancelling the spatial portal modal opens nothing', async ({ page }) => {
    const { qidRequests, openedUrls } = await stubExternalLinks(page);

    await page.goto(FILTERED_URL);
    const button = page.getByRole('button', { name: /View in spatial portal/i });
    await expect(button).toBeVisible();
    await button.click();

    await expect(modal(page)).toBeVisible();
    await cancelButton(page).click();

    await expect(modal(page)).not.toBeVisible();
    await expect.poll(openedUrls).toHaveLength(0);
    expect(qidRequests.length).toBe(0);
  });
});
