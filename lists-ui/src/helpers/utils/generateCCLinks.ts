/**
 * Generates a Creative Commons license URL from a short-form licence value.
 * @param shortForm - The licence code as stored in constraints (e.g., 'CC-BY-SA', 'CC0')
 * @param version - The licence version (defaulting to '4.0')
 * @returns The full URL to the licence deed
 */
export function generateCCLink(shortForm: string, version: string = '4.0'): string {
  const normalized = shortForm.toLowerCase().replace('cc', '').replace(/-/g, '');

  if (normalized === '0' || normalized === 'zero') {
    return `https://creativecommons.org/publicdomain/zero/1.0/`;
  }

  const parts = shortForm.toLowerCase()
    .replace('cc-', '')
    .split('-')
    .filter(part => ['by', 'nc', 'sa', 'nd'].includes(part));

  const licenseCode = parts.join('-');
  return `https://creativecommons.org/licenses/${licenseCode}/${version}/`;
}