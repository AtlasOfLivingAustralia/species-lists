import { Page } from '@playwright/test';

/** The value of a CSS custom property on <html>, which is how the theme's colours arrive. */
export function cssVariable(page: Page, name: string): Promise<string> {
  return page.evaluate(
    (property) => getComputedStyle(document.documentElement).getPropertyValue(property).trim(),
    name
  );
}
