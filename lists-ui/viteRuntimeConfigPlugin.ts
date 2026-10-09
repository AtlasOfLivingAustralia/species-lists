import fs from 'node:fs';
import path from 'node:path';
import type { Plugin, ResolvedConfig } from 'vite';
import { findUnsupportedAppEnvUsage, rewriteAppEnvReferences } from './runtimeAppConfigTransform.ts';

/**
 * Picks one of two build profiles, from the same code and the same commit:
 *
 *   default (ALA) build   adds nothing: the <script src="/config.js"> and config.local.js tags are
 *                         removed from index.html and community/ is not copied to the output.
 *   community build       with VITE_RUNTIME_CONFIG_ENABLED=true (see config/.env.community and
 *                         `yarn build:community`) the tags stay and community/ is copied, so a
 *                         deployer can point the built app at their own backend and identity
 *                         provider afterwards, without rebuilding. Every
 *                         `import.meta.env.VITE_*` in the app's sources is also rewritten to
 *                         `getAppConfigValue(key, import.meta.env.VITE_*)`, so any variable can be
 *                         overridden from config.js without listing keys or touching the sources.
 *
 * community/ sits outside public/ on purpose, because Vite copies public/ into every build.
 */
export const RUNTIME_CONFIG_FLAG = 'VITE_RUNTIME_CONFIG_ENABLED';
const COMMUNITY_DIR = 'community';
/** A line that holds nothing but one of the two runtime config script tags. */
const RUNTIME_CONFIG_SCRIPT_LINE = /^[ \t]*<script\s+src="\/config(?:\.local)?\.js"><\/script>[ \t]*\r?$/;
/** Any script tag that loads one of them, however it is written. */
const RUNTIME_CONFIG_SCRIPT_ANY = /<script\b[^>]*\bsrc=["']\/config(?:\.local)?\.js["']/i;
/** Resolved by the `#` alias in vite.config.ts, so the rewritten sources can import it from anywhere. */
const APP_CONFIG_HELPER = '#/helpers/utils/runtimeConfig';
const APP_SOURCE_RE = /\.(ts|tsx|js|jsx)$/;

/**
 * Removes the config.js and config.local.js tags, which index.html keeps each on a line of its own.
 * It drops whole lines rather than replacing a pattern inside the text, so a removal can never
 * splice the surrounding text into a new tag. If a tag is still there afterwards (written inline,
 * or split across lines) it fails instead of silently shipping it in the default build.
 */
export function stripRuntimeConfigScripts(html: string): string {
  const out = html
    .split('\n')
    .filter((line) => !RUNTIME_CONFIG_SCRIPT_LINE.test(line))
    .join('\n');
  if (RUNTIME_CONFIG_SCRIPT_ANY.test(out)) {
    throw new Error(
      '[lists-runtime-config] index.html loads /config.js or /config.local.js in a form the default build ' +
        'cannot remove. Keep each of those script tags on a line of its own.'
    );
  }
  return out;
}

export function runtimeConfigPlugin(): Plugin {
  let resolved: ResolvedConfig;
  let enabled = false;
  let communityDir = '';

  return {
    name: 'lists-runtime-config',

    configResolved(config) {
      resolved = config;
      // vite.config.ts merges config/.env.* into process.env before the plugins are created
      enabled = process.env[RUNTIME_CONFIG_FLAG] === 'true';
      communityDir = path.resolve(config.root, COMMUNITY_DIR);
      config.logger.info(
        enabled
          ? '[lists-runtime-config] community build: config.js kept, community/ copied'
          : '[lists-runtime-config] default build: no runtime configuration'
      );
    },

    // order 'pre': Vite's own HTML plugin warns that /config.js is not a module, so the tags have
    // to be gone before it runs.
    transformIndexHtml: {
      order: 'pre',
      handler(html: string) {
        return enabled ? html : stripRuntimeConfigScripts(html);
      },
    },

    // Community build only: make every `import.meta.env.VITE_*` overridable at runtime. 'pre' so it
    // runs before Vite's own define step replaces `import.meta.env.*` with values.
    transform: {
      order: 'pre',
      handler(this: { error: (message: string) => never }, code: string, id: string) {
        if (!enabled) return null;
        const file = id.split('?')[0].replace(/\\/g, '/');
        const src = `${resolved.root.replace(/\\/g, '/')}/src/`;
        if (!file.startsWith(src) || !APP_SOURCE_RE.test(file) || file.endsWith('.d.ts')) return null;
        const unsupported = findUnsupportedAppEnvUsage(code);
        if (unsupported.length > 0) {
          this.error(
            `[lists-runtime-config] ${file}: ${unsupported.join('; ')}. Only the literal form ` +
              '`import.meta.env.VITE_X` can be overridden from config.js; use it, or the key will ignore config.js.'
          );
        }
        const out = rewriteAppEnvReferences(code, APP_CONFIG_HELPER);
        return out === null ? null : { code: out, map: null };
      },
    },

    // community/ is outside publicDir, so the dev server has to serve it explicitly.
    configureServer(server) {
      if (!enabled || !fs.existsSync(communityDir)) return;
      server.middlewares.use((req, res, next) => {
        const url = (req.url ?? '').split('?')[0];
        const file = path.resolve(communityDir, `.${path.posix.normalize(url)}`);
        // path.relative rather than startsWith: the latter also accepts a sibling directory whose
        // name merely starts with this one's
        const inside = path.relative(communityDir, file);
        if (inside.startsWith('..') || path.isAbsolute(inside)) return next();
        if (!fs.existsSync(file) || !fs.statSync(file).isFile()) {
          // config.local.js is optional: answer 404 instead of letting the SPA fallback serve
          // index.html as a script
          if (url === '/config.local.js') {
            res.statusCode = 404;
            return res.end();
          }
          return next();
        }
        if (file.endsWith('.js')) res.setHeader('Content-Type', 'text/javascript');
        res.end(fs.readFileSync(file));
      });
    },

    closeBundle() {
      if (resolved.command !== 'build' || !enabled || !fs.existsSync(communityDir)) return;
      fs.cpSync(communityDir, path.resolve(resolved.root, resolved.build.outDir), {
        recursive: true,
      });
    },
  };
}
