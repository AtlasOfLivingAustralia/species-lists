import { test, expect } from '../fixtures/test';

test.describe('12. Species Lookup API Endpoint', () => {
  test('returns valid JSON without 4xx or 5xx errors for species GUID lookup', async ({ page }) => {
    await page.goto('/');
    const guid = encodeURIComponent('https://biodiversity.org.au/afd/taxa/03cd7f73-0b66-4c48-b4aa-6883e729897f');
    const result = await page.evaluate(async (url) => {
      const res = await fetch(url);
      return {
        status: res.status,
        data: await res.json(),
      };
    }, `/ws/species/${guid}`);

    expect(result.status).toBeLessThan(400);
    expect(result.data).toBeDefined();
  });
});
