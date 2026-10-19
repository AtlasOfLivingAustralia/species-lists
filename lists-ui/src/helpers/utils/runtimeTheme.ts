/**
 * Portal look & feel that a deployment can change without rebuilding: header and footer templates,
 * extra CSS/JS, the primary colour, the favicon and analytics.
 *
 * The values come from `import.meta.env.VITE_THEME_*` and friends, read by the callers (main.tsx,
 * Dashboard) in the literal form, so the community build makes each of them overridable from
 * config.js / config.local.js (see runtimeConfig.ts). The default (ALA) build leaves them empty and
 * renders exactly as before.
 *
 * The header/footer contract is the one atlas-index uses (common-ui/src/components/header.tsx), so
 * one banner.mustache / footer.mustache serves both apps.
 *
 * Everything here is a plain function that takes its values and the document as arguments, so it
 * can be tested with node:test.
 */

/** The parts of `document` these helpers touch, so tests can pass a stand-in. */
export type ThemeDocument = Pick<Document, 'createElement' | 'head' | 'body' | 'querySelector'>;

/**
 * Fills a header/footer mustache template the same way atlas-index does:
 *   {{containerClass}}               -> containerClass
 *   {{searchServer}}{{searchPath}}   -> searchUrl, when given
 *   any other {{ / }}                -> removed, keeping the name ({{loginStatus}} -> loginStatus)
 */
export function cleanMustache(template: string, containerClass: string, searchUrl?: string): string {
  let out = template.replace(/\{\{containerClass}}/g, containerClass);
  if (searchUrl) {
    out = out.replace(/\{\{searchServer}}\{\{searchPath}}/g, searchUrl);
  }
  return out.replace(/\{\{|}}/g, '');
}

/** A comma-separated list of URLs, trimmed, without empty entries. */
export function splitUrlList(value: string | undefined): string[] {
  return (value ?? '')
    .split(',')
    .map((url) => url.trim())
    .filter((url) => url.length > 0);
}

/**
 * Appends one stylesheet per URL to <head>, in the order given. They go after the app's own CSS,
 * so for equal specificity the theme wins, and among them the last one wins.
 */
export function injectThemeCss(value: string | undefined, doc: ThemeDocument = document): void {
  for (const url of splitUrlList(value)) {
    const link = doc.createElement('link');
    link.rel = 'stylesheet';
    link.href = url;
    doc.head.appendChild(link);
  }
}

/**
 * Loads scripts one after another, in the order given, so a later one can depend on an earlier
 * one. Call it once the header is in the DOM: theme scripts usually look for its elements.
 */
export function injectThemeJs(value: string | undefined, doc: ThemeDocument = document): void {
  const urls = splitUrlList(value);
  const loadNext = (index: number) => {
    if (index >= urls.length) return;
    const script = doc.createElement('script');
    script.src = urls[index];
    script.onload = () => loadNext(index + 1);
    script.onerror = () => loadNext(index + 1);
    doc.body.appendChild(script);
  };
  loadNext(0);
}

/** Fathom analytics, only when a site id is set. The community build leaves it empty. */
export function injectFathom(siteId: string | undefined, doc: ThemeDocument = document): void {
  if (!siteId || !siteId.trim()) return;
  const script = doc.createElement('script');
  script.src = 'https://cdn.usefathom.com/script.js';
  script.defer = true;
  script.setAttribute('data-site', siteId.trim());
  script.setAttribute('data-spa', 'auto');
  doc.head.appendChild(script);
}

/** Points the page's icon at `url`, when given. */
export function setFavicon(url: string | undefined, doc: ThemeDocument = document): void {
  if (!url || !url.trim()) return;
  let link = doc.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (!link) {
    link = doc.createElement('link');
    link.rel = 'icon';
    doc.head.appendChild(link);
  }
  link.removeAttribute('type');
  link.href = url.trim();
}

/** Name of the palette built from a CSS colour given as the primary colour. */
export const PORTAL_COLOR = 'portal';
const HEX_COLOR = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i;

export interface PrimaryColorOverride {
  primaryColor: string;
  colors?: Record<string, string[]>;
}

/**
 * Theme override for a primary colour, or null to keep the build's own.
 *
 * `value` is either the name of a palette the theme already has (`colorNames`: Mantine's defaults
 * such as `violet` or `teal`, and ala-mantine's such as `rust`), or a hex colour, which becomes a
 * flat ten-shade palette. Anything else is ignored with a warning: Mantine fails to render with a
 * primary colour it does not know.
 */
export function primaryColorOverride(
  value: string | undefined,
  colorNames: string[]
): PrimaryColorOverride | null {
  const color = value?.trim();
  if (!color) return null;
  if (colorNames.includes(color)) return { primaryColor: color };
  if (HEX_COLOR.test(color)) {
    return { primaryColor: PORTAL_COLOR, colors: { [PORTAL_COLOR]: Array(10).fill(color) } };
  }
  console.warn(
    `[lists-theme] VITE_THEME_PRIMARY_COLOR "${color}" is neither a theme colour (${colorNames.join(', ')}) ` +
      'nor a hex colour; keeping the default.'
  );
  return null;
}

/**
 * Toggles `signedIn` / `signedOut` on every `.loginStatus` element under `root`, which the
 * template's CSS uses to show the login or the logout button.
 */
export function showLoginStatus(root: ParentNode, isLoggedIn: boolean): void {
  root.querySelectorAll('.loginStatus').forEach((el) => {
    el.classList.toggle('signedIn', isLoggedIn);
    el.classList.toggle('signedOut', !isLoggedIn);
  });
}
