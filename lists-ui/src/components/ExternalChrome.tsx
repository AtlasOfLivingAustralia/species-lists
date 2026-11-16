import DOMPurify from 'dompurify';
import { useEffect, useRef, useState } from 'react';

import {
  cleanMustache,
  createThemeJsGate,
  extractInertTemplates,
  injectThemeJs,
  prepareAuthLinks,
  showLoginStatus,
} from '#/helpers/utils/runtimeTheme';

// Theme scripts (jQuery, Bootstrap...) load once per page, after every header/footer is in the DOM
const themeJs = createThemeJsGate((jsUrl) => injectThemeJs(jsUrl));

// Each header/footer once built, by template URL. Theme scripts set it up only once (translations,
// handlers), so when the Dashboard remounts, e.g. after the router shows its error page and
// recovers, these same nodes go back rather than an uninitialised copy
const built = new Map<string, Node[]>();

interface ExternalChromeProps {
  /** URL of the mustache template; nothing renders without it. */
  url: string;
  containerClass: string;
  isAuthenticated: boolean;
  onLogin: () => void;
  onLogout: () => void;
}

interface ExternalHeaderProps extends ExternalChromeProps {
  /** Replaces {{searchServer}}{{searchPath}} in the template. */
  searchUrl?: string;
  /** Comma-separated scripts to load once the header is in the DOM. */
  jsUrl?: string;
}

/**
 * Renders a portal's own header or footer from a mustache template, with the same contract as
 * atlas-index's Header/Footer (common-ui/src/components/header.tsx), so one template serves both:
 *
 *   {{containerClass}}              container class (VITE_THEME_CONTAINER_CLASS)
 *   {{searchServer}}{{searchPath}}  search URL (VITE_THEME_SEARCH_URL), header only
 *   other {{name}}                  braces removed, e.g. class="{{loginStatus}}" -> "loginStatus"
 *   .loginStatus                    gets `signedIn` or `signedOut` with the sign-in state
 *   .loginBtn / .logoutBtn          sign in / out through OIDC; their href becomes #
 *
 * The template is sanitised with DOMPurify into DOM nodes, so a <script> in it never runs; theme
 * scripts go in VITE_THEME_JS_URL instead. Only `<script type="text/html" id="...">` blocks are
 * re-added, as inert text, because theme scripts read them as templates (ALA's autocomplete). The
 * browser never runs them, but a theme script may compile one into code (ALA's tmpl() does), so a
 * template needs the same trust as VITE_THEME_JS_URL.
 */
function ExternalChrome({
  url,
  containerClass,
  searchUrl,
  isAuthenticated,
  onLogin,
  onLogout,
}: ExternalChromeProps & { searchUrl?: string }) {
  const [nodes, setNodes] = useState(() => built.get(url));
  const ref = useRef<HTMLDivElement>(null);

  // The click handler is bound once; this keeps it calling the current callbacks
  const handlers = useRef({ onLogin, onLogout });
  handlers.current = { onLogin, onLogout };

  useEffect(() => {
    if (built.has(url)) return;
    themeJs.expect(url);
    let cancelled = false;
    fetch(url)
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.text();
      })
      .then((text) => {
        if (cancelled) return;
        const chrome = buildChrome(cleanMustache(text, containerClass, searchUrl));
        built.set(url, chrome);
        setNodes(chrome);
      })
      .catch((error) => {
        console.error(`[lists-theme] Could not load ${url}`, error);
        // The theme scripts still load for the other part
        if (!cancelled) themeJs.ready(url);
      });
    return () => {
      cancelled = true;
    };
  }, [url, containerClass, searchUrl]);

  // Added by hand rather than rendered, so React never re-applies it over the classes toggled below
  useEffect(() => {
    const root = ref.current;
    if (!root || !nodes) return;
    root.replaceChildren(...nodes);
    themeJs.ready(url);
  }, [nodes, url]);

  useEffect(() => {
    if (ref.current && nodes) showLoginStatus(ref.current, isAuthenticated);
  }, [nodes, isAuthenticated]);

  // One delegated listener on the container, so re-renders never stack handlers
  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    const onClick = (event: MouseEvent) => {
      const target = event.target as Element | null;
      if (target?.closest('.loginBtn')) {
        event.preventDefault();
        handlers.current.onLogin();
      } else if (target?.closest('.logoutBtn')) {
        event.preventDefault();
        handlers.current.onLogout();
      }
    };
    root.addEventListener('click', onClick);
    return () => root.removeEventListener('click', onClick);
  }, []);

  return <div ref={ref} />;
}

/** The sanitised template as DOM nodes, plus its inert templates for theme scripts. */
function buildChrome(template: string): Node[] {
  const holder = document.createElement('div');
  holder.appendChild(DOMPurify.sanitize(template, { RETURN_DOM_FRAGMENT: true }));
  // Inert templates for theme scripts (e.g. #autoCompleteTemplate), as text, never run
  const parsed = new DOMParser().parseFromString(template, 'text/html');
  for (const { id, content } of extractInertTemplates(parsed)) {
    if (document.getElementById(id)) continue;
    const script = document.createElement('script');
    script.type = 'text/html';
    script.id = id;
    script.textContent = content;
    holder.appendChild(script);
  }
  prepareAuthLinks(holder);
  return Array.from(holder.childNodes);
}

export function ExternalHeader({ jsUrl, ...props }: ExternalHeaderProps) {
  // Theme scripts look for the header's elements, so they load once it is in the DOM
  useEffect(() => {
    if (jsUrl) themeJs.setJs(jsUrl);
  }, [jsUrl]);
  return <ExternalChrome {...props} />;
}

export function ExternalFooter(props: ExternalChromeProps) {
  return <ExternalChrome {...props} />;
}
