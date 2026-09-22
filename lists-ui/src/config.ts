declare global {
  interface Window {
    __APP_CONFIG__?: Record<string, string>;
  }
}

/**
 * Reads a value that a deployment may need to change without rebuilding the app
 * (backend URL, OIDC settings). Checks window.__APP_CONFIG__ (set by an optional
 * /config.js a deployment can serve, see public/config.example.js) before falling
 * back to the build-time VITE_* value, so ALA's default build is unaffected unless
 * a deployment opts in.
 */
export function getConfig(key: string): string {
  const runtimeValue = window.__APP_CONFIG__?.[key];
  if (runtimeValue !== undefined) return runtimeValue;
  return (import.meta.env as Record<string, string | undefined>)[key] ?? '';
}

export {};
