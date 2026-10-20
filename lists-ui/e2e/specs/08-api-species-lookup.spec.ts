import { test, expect } from '@playwright/test';

test.describe('12. Species Lookup API Endpoint', () => {
  test('returns valid JSON without 4xx or 5xx errors for species GUID lookup', async ({ request }) => {
    const apiBaseUrl = process.env.API_BASE_URL;
    test.skip(!apiBaseUrl, 'Skipping unmocked API test: API_BASE_URL is not configured');

    const guid = encodeURIComponent('https://biodiversity.org.au/afd/taxa/03cd7f73-0b66-4c48-b4aa-6883e729897f');
    const res = await request.get(`${apiBaseUrl}/ws/species/${guid}`);

    expect(res.status()).toBeLessThan(400);
    const data = await res.json();
    expect(data).toBeDefined();
  });
});
