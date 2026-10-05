import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { findUnsupportedAppEnvUsage, rewriteAppEnvReferences } from '../runtimeAppConfigTransform.ts';

const HELPER = '#/helpers/utils/runtimeConfig';

describe('rewriteAppEnvReferences', () => {
  it('wraps each VITE_* read, keeping the build value as the fallback', () => {
    const out = rewriteAppEnvReferences('const a = import.meta.env.VITE_API_BASEURL + "/x";', HELPER);
    assert.ok(out);
    assert.match(
      out,
      /__getAppConfigValue\('VITE_API_BASEURL', import\.meta\.env\.VITE_API_BASEURL\) \+ "\/x"/
    );
  });

  it('adds the helper import on the same first line, so line numbers do not move', () => {
    const code = 'const a = import.meta.env.VITE_A;\nconst b = import.meta.env.VITE_B;\n';
    const out = rewriteAppEnvReferences(code, HELPER)!;
    assert.equal(out.split('\n').length, code.split('\n').length);
    assert.ok(out.startsWith(`import { getAppConfigValue as __getAppConfigValue } from "${HELPER}";const a`));
  });

  it('rewrites every occurrence, including template literals', () => {
    const out = rewriteAppEnvReferences('`${import.meta.env.VITE_A}/${import.meta.env.VITE_B}`', HELPER)!;
    assert.equal(out.match(/__getAppConfigValue\('/g)?.length, 2);
  });

  it('does not match a longer name that merely starts with a key', () => {
    const out = rewriteAppEnvReferences('import.meta.env.VITE_X_Y', HELPER)!;
    assert.match(out, /__getAppConfigValue\('VITE_X_Y', import\.meta\.env\.VITE_X_Y\)/);
  });

  it('leaves non VITE_ reads alone and returns null when there is nothing to rewrite', () => {
    assert.equal(rewriteAppEnvReferences('if (import.meta.env.DEV) {}', HELPER), null);
    assert.equal(rewriteAppEnvReferences('const x = 1;', HELPER), null);
  });
});

describe('findUnsupportedAppEnvUsage', () => {
  it('accepts the literal form', () => {
    assert.deepEqual(findUnsupportedAppEnvUsage('const a = import.meta.env.VITE_A; if (import.meta.env.DEV) {}'), []);
  });

  it('flags computed access', () => {
    assert.equal(findUnsupportedAppEnvUsage('const v = import.meta.env[key];').length, 1);
  });

  it('flags destructuring', () => {
    assert.equal(findUnsupportedAppEnvUsage('const { VITE_A } = import.meta.env;').length >= 1, true);
  });

  it('flags import.meta.env passed as a whole object', () => {
    assert.equal(findUnsupportedAppEnvUsage('log(import.meta.env);').length, 1);
  });

  it('ignores prose in comments that mentions the syntax', () => {
    assert.deepEqual(
      findUnsupportedAppEnvUsage('// do not use import.meta.env[key]\n/* or { VITE_A } = import.meta.env */\nconst a = 1;'),
      []
    );
  });
});
