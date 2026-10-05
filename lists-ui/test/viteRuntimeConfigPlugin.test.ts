import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, it } from 'node:test';
import { RUNTIME_CONFIG_FLAG, runtimeConfigPlugin } from '../viteRuntimeConfigPlugin.ts';

// The plugin is exercised through its hooks, with just enough of Vite's resolved config
type Hook = (...args: any[]) => any;
const hooks = (plugin: any) => ({
  configResolved: plugin.configResolved as Hook,
  html: plugin.transformIndexHtml.handler as Hook,
  transform: plugin.transform.handler as Hook,
  closeBundle: plugin.closeBundle as Hook,
});

const INDEX_HTML = fs.readFileSync(path.resolve(import.meta.dirname, '../index.html'), 'utf-8');

let root: string;
const savedFlag = process.env[RUNTIME_CONFIG_FLAG];

function resolve(plugin: any, command: 'build' | 'serve' = 'build') {
  hooks(plugin).configResolved({
    root,
    command,
    build: { outDir: 'dist' },
    logger: { info() {} },
  });
}

beforeEach(() => {
  root = fs.mkdtempSync(path.join(os.tmpdir(), 'lists-ui-plugin-'));
  fs.mkdirSync(path.join(root, 'community'));
  fs.writeFileSync(path.join(root, 'community', 'config.js'), 'window.APP_CONFIG = {};\n');
  fs.mkdirSync(path.join(root, 'dist'));
});

afterEach(() => {
  fs.rmSync(root, { recursive: true, force: true });
  if (savedFlag === undefined) delete process.env[RUNTIME_CONFIG_FLAG];
  else process.env[RUNTIME_CONFIG_FLAG] = savedFlag;
});

describe('runtimeConfigPlugin, default (ALA) build', () => {
  it('removes both script tags and nothing else from index.html', () => {
    delete process.env[RUNTIME_CONFIG_FLAG];
    const plugin = runtimeConfigPlugin();
    resolve(plugin);

    const out = hooks(plugin).html(INDEX_HTML);

    assert.doesNotMatch(out, /config(\.local)?\.js/);
    const expected = INDEX_HTML.split('\n')
      .filter((line) => !/config(\.local)?\.js/.test(line))
      .join('\n');
    assert.equal(out, expected);
  });

  it('is also the behaviour when the flag is anything but "true"', () => {
    process.env[RUNTIME_CONFIG_FLAG] = 'false';
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    assert.doesNotMatch(hooks(plugin).html(INDEX_HTML), /config\.js/);
  });

  it('does not copy community/ to the output', () => {
    delete process.env[RUNTIME_CONFIG_FLAG];
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    hooks(plugin).closeBundle();
    assert.equal(fs.existsSync(path.join(root, 'dist', 'config.js')), false);
  });
});

describe('runtimeConfigPlugin, community build', () => {
  it('keeps index.html untouched', () => {
    process.env[RUNTIME_CONFIG_FLAG] = 'true';
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    assert.equal(hooks(plugin).html(INDEX_HTML), INDEX_HTML);
    assert.match(INDEX_HTML, /<script src="\/config\.js"><\/script>/);
    assert.match(INDEX_HTML, /<script src="\/config\.local\.js"><\/script>/);
  });

  it('copies community/ to the output on build', () => {
    process.env[RUNTIME_CONFIG_FLAG] = 'true';
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    hooks(plugin).closeBundle();
    assert.equal(
      fs.readFileSync(path.join(root, 'dist', 'config.js'), 'utf-8'),
      'window.APP_CONFIG = {};\n'
    );
  });

  it('does not copy anything when the dev server closes', () => {
    process.env[RUNTIME_CONFIG_FLAG] = 'true';
    const plugin = runtimeConfigPlugin();
    resolve(plugin, 'serve');
    hooks(plugin).closeBundle();
    assert.equal(fs.existsSync(path.join(root, 'dist', 'config.js')), false);
  });
});

describe('runtimeConfigPlugin, transform', () => {
  const ctx = {
    error(message: string): never {
      throw new Error(message);
    },
  };
  const source = 'export const u = import.meta.env.VITE_API_BASEURL;';

  it('does nothing in the default build', () => {
    delete process.env[RUNTIME_CONFIG_FLAG];
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    assert.equal(hooks(plugin).transform.call(ctx, source, `${root}/src/api/csrf.ts`), null);
  });

  it('rewrites VITE_* reads in the app sources of the community build', () => {
    process.env[RUNTIME_CONFIG_FLAG] = 'true';
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    const out = hooks(plugin).transform.call(ctx, source, `${root}/src/api/csrf.ts?v=1`);
    assert.match(out.code, /__getAppConfigValue\('VITE_API_BASEURL', import\.meta\.env\.VITE_API_BASEURL\)/);
  });

  it('leaves dependencies, files outside src and declaration files alone', () => {
    process.env[RUNTIME_CONFIG_FLAG] = 'true';
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    const t = hooks(plugin).transform;
    assert.equal(t.call(ctx, source, `${root}/node_modules/dep/index.js`), null);
    assert.equal(t.call(ctx, source, `${root}/vite.config.ts`), null);
    assert.equal(t.call(ctx, source, `${root}/src/vite-env.d.ts`), null);
    assert.equal(t.call(ctx, source, `${root}/src/logo.svg`), null);
  });

  it('fails the build on a form it cannot rewrite, so a key cannot ignore config.js', () => {
    process.env[RUNTIME_CONFIG_FLAG] = 'true';
    const plugin = runtimeConfigPlugin();
    resolve(plugin);
    assert.throws(
      () => hooks(plugin).transform.call(ctx, 'const v = import.meta.env[name];', `${root}/src/a.ts`),
      /computed access/
    );
  });
});
