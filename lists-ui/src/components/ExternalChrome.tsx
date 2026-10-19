import DOMPurify from 'dompurify';
import { useEffect, useRef, useState } from 'react';

import {
  cleanMustache,
  extractInertTemplates,
  injectThemeJs,
  showLoginStatus,
} from '#/helpers/utils/runtimeTheme';

// Theme scripts (jQuery, Bootstrap...) must load once per page, even if the header remounts, e.g.
// after the router shows its error page and recovers
let themeJsLoaded = false;

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
 *   .loginBtn / .logoutBtn          sign in / out through OIDC; their href is removed
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
  onReady,
}: ExternalChromeProps & { searchUrl?: string; onReady?: () => void }) {
  const [template, setTemplate] = useState('');
  const ref = useRef<HTMLDivElement>(null);

  // The click handler is bound once; these keep it calling the current callbacks
  const handlers = useRef({ onLogin, onLogout });
  handlers.current = { onLogin, onLogout };
  const ready = useRef(onReady);
  ready.current = onReady;

  useEffect(() => {
    let cancelled = false;
    fetch(url)
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.text();
      })
      .then((text) => {
        if (!cancelled) setTemplate(cleanMustache(text, containerClass, searchUrl));
      })
      .catch((error) => {
        console.error(`[lists-theme] Could not load ${url}`, error);
      });
    return () => {
      cancelled = true;
    };
  }, [url, containerClass, searchUrl]);

  // Added by hand rather than rendered, so React never re-applies it over the classes toggled below
  useEffect(() => {
    const root = ref.current;
    if (!root || !template) return;
    root.replaceChildren(DOMPurify.sanitize(template, { RETURN_DOM_FRAGMENT: true }));
    // Inert templates for theme scripts (e.g. #autoCompleteTemplate), as text, never run
    for (const { id, content } of extractInertTemplates(template)) {
      if (document.getElementById(id)) continue;
      const script = document.createElement('script');
      script.type = 'text/html';
      script.id = id;
      script.textContent = content;
      root.appendChild(script);
    }
    root.querySelectorAll('.loginBtn, .logoutBtn').forEach((el) => el.removeAttribute('href'));
    ready.current?.();
  }, [template]);

  useEffect(() => {
    if (ref.current && template) showLoginStatus(ref.current, isAuthenticated);
  }, [template, isAuthenticated]);

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

export function ExternalHeader({ jsUrl, ...props }: ExternalHeaderProps) {
  // Theme scripts look for the header's elements, so they load once it is in the DOM
  const loadJs = () => {
    if (themeJsLoaded || !jsUrl) return;
    themeJsLoaded = true;
    injectThemeJs(jsUrl);
  };
  return <ExternalChrome {...props} onReady={loadJs} />;
}

export function ExternalFooter(props: ExternalChromeProps) {
  return <ExternalChrome {...props} />;
}
