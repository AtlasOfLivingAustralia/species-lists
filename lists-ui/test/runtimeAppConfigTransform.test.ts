import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { findUnsupportedAppEnvUsage, rewriteAppEnvReferences } from '../runtimeAppConfigTransform.ts';

const HELPER = '#/helpers/utils/runtimeConfig';
const TS = 'src/a.ts';
const TSX = 'src/a.tsx';
const rewrite = (code: string, file = TS) => rewriteAppEnvReferences(code, HELPER, file);
const unsupported = (code: string, file = TS) => findUnsupportedAppEnvUsage(code, file);
const IMPORT = `import { getAppConfigValue as __getAppConfigValue } from "${HELPER}";`;

describe('rewriteAppEnvReferences', () => {
  it('wraps each VITE_* read, keeping the build value as the fallback', () => {
    const out = rewrite('const a = import.meta.env.VITE_API_BASEURL + "/x";');
    assert.ok(out);
    assert.match(
      out,
      /__getAppConfigValue\('VITE_API_BASEURL', import\.meta\.env\.VITE_API_BASEURL\) \+ "\/x"/
    );
  });

  it('adds the helper import on the same first line, so line numbers do not move', () => {
    const code = 'const a = import.meta.env.VITE_A;\nconst b = import.meta.env.VITE_B;\n';
    const out = rewrite(code)!;
    assert.equal(out.split('\n').length, code.split('\n').length);
    assert.ok(out.startsWith(`${IMPORT}const a`));
  });

  it('rewrites every occurrence, including template interpolations', () => {
    const out = rewrite('const u = `${import.meta.env.VITE_A}/${import.meta.env.VITE_B}`;')!;
    assert.equal(out.match(/__getAppConfigValue\('/g)?.length, 2);
  });

  it('does not match a longer name that merely starts with a key', () => {
    const out = rewrite('const v = import.meta.env.VITE_X_Y;')!;
    assert.match(out, /__getAppConfigValue\('VITE_X_Y', import\.meta\.env\.VITE_X_Y\)/);
  });

  it('leaves non VITE_ reads alone and returns null when there is nothing to rewrite', () => {
    assert.equal(rewrite('if (import.meta.env.DEV) {}'), null);
    assert.equal(rewrite('const x = 1;'), null);
  });

  it('leaves the text alone inside strings and comments', () => {
    assert.equal(rewrite("const help = 'import.meta.env.VITE_API_BASEURL';"), null);
    assert.equal(rewrite('const help = "import.meta.env.VITE_API_BASEURL";'), null);
    assert.equal(rewrite('// import.meta.env.VITE_A\n/* import.meta.env.VITE_B */\nconst x = 1;'), null);
  });

  it('rewrites only the interpolation of a template, not its literal text', () => {
    const out = rewrite('const t = `import.meta.env.VITE_A is ${import.meta.env.VITE_A}`;')!;
    assert.equal(
      out,
      `${IMPORT}const t = \`import.meta.env.VITE_A is \${__getAppConfigValue('VITE_A', import.meta.env.VITE_A)}\`;`
    );
  });

  it('leaves a JSX attribute string alone and rewrites a JSX expression', () => {
    const out = rewrite(
      'const e = <a title="import.meta.env.VITE_A" href={import.meta.env.VITE_B}>x</a>;',
      TSX
    )!;
    assert.match(out, /title="import\.meta\.env\.VITE_A"/);
    assert.match(out, /href=\{__getAppConfigValue\('VITE_B', import\.meta\.env\.VITE_B\)\}/);
  });

  it('parses .ts files as TypeScript, so angle bracket casts survive', () => {
    const out = rewrite('const u = <string>import.meta.env.VITE_A;')!;
    assert.equal(out, `${IMPORT}const u = <string>__getAppConfigValue('VITE_A', import.meta.env.VITE_A);`);
  });
});

describe('findUnsupportedAppEnvUsage', () => {
  it('accepts the literal form', () => {
    assert.deepEqual(unsupported('const a = import.meta.env.VITE_A; if (import.meta.env.DEV) {}'), []);
  });

  it('flags computed access', () => {
    assert.match(unsupported('const v = import.meta.env[key];').join(), /computed access/);
  });

  it('flags destructuring', () => {
    assert.match(unsupported('const { VITE_A } = import.meta.env;').join(), /destructuring/);
  });

  it('flags import.meta.env used as a whole object, with its line', () => {
    assert.deepEqual(unsupported('\nlog(import.meta.env);'), ['import.meta.env used as a whole object (line 2)']);
    assert.equal(unsupported('const env = { ...import.meta.env };').length, 1);
    assert.equal(unsupported('function f() { return import.meta.env; }').length, 1);
  });

  it('flags writes and optional chaining, which cannot be rewritten into a call', () => {
    assert.match(unsupported('import.meta.env.VITE_A = "x";').join(), /assignment/);
    assert.match(unsupported('const v = import.meta.env?.VITE_A;').join(), /optional chaining/);
  });

  it('ignores prose in comments and strings that mentions the syntax', () => {
    assert.deepEqual(
      unsupported('// do not use import.meta.env[key]\n/* or { VITE_A } = import.meta.env */\nconst s = "import.meta.env";'),
      []
    );
  });
});
