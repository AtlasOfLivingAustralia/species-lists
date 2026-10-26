/**
 * ESLint 9 flat configuration.
 *
 * `yarn lint` has always been in package.json, but there was no configuration file in the
 * repository, so the command only ever printed ESLint's "couldn't find a configuration file"
 * error. This is that missing file.
 *
 * It stays deliberately small. TypeScript already does the heavy lifting (`yarn typecheck`, with
 * `strict`, `noUnusedLocals` and `noUnusedParameters`), so the rules here are the ones the
 * compiler cannot express: the React hook rules, and a handful of correctness rules. Rules are
 * listed one by one rather than spread from a plugin preset, so enabling one is always a
 * deliberate change and `yarn lint` keeps passing with zero warnings (the script runs with
 * `--max-warnings 0`, so a warning fails the build just like an error).
 *
 * Import order (third-party, then `#/` absolute, then relative, then CSS modules) is a convention
 * from AGENTS.md; nothing enforces it, as eslint-plugin-import is not a dependency.
 */
import tsPlugin from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';

export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'e2e/playwright-report/**',
      'e2e/test-results/**',
      // Plain browser scripts a deployment edits by hand, not application sources
      'community/**',
    ],
  },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: tsParser,
      ecmaVersion: 2022,
      sourceType: 'module',
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    linterOptions: {
      reportUnusedDisableDirectives: 'off',
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      // Hooks: the one class of bug TypeScript cannot see
      'react-hooks/rules-of-hooks': 'error',

      // Correctness
      'no-debugger': 'error',
      'no-dupe-class-members': 'off',
      '@typescript-eslint/no-dupe-class-members': 'error',
      'no-duplicate-case': 'error',
      'no-unsafe-negation': 'error',
      'no-unsafe-optional-chaining': 'error',
      'no-constant-condition': ['error', { checkLoops: false }],
      'no-fallthrough': 'error',
      'no-self-assign': 'error',
      'no-self-compare': 'error',
      'no-sparse-arrays': 'error',
      'no-unused-labels': 'error',
      'no-useless-escape': 'error',
      'require-atomic-updates': 'off',
      'valid-typeof': 'error',

      // Unused symbols: TypeScript reports these too, but only for the files it compiles
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
          ignoreRestSiblings: true,
        },
      ],

      // Not enabled yet: the two rules the sources already carry `eslint-disable` comments for.
      // Turning either on today fails `yarn lint` (as of this commit: 19 exhaustive-deps and 15
      // no-explicit-any findings), and an exhaustive-deps fix changes when effects re-run, so they
      // are the next step rather than part of making the command work at all. The existing
      // disable comments are left in place for when they are turned on, which is why the lint
      // script does not pass --report-unused-disable-directives.
      'react-hooks/exhaustive-deps': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
  {
    // Fast refresh only works when a module exports components and nothing else. The views export
    // loaders and constants alongside their component, so this is left to the editor.
    files: ['src/**/*.tsx'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
  {
    // Playwright fixtures and specs are Node, not React. Their fixture callback is called `use`,
    // which the React hook rules read as the `use` hook and reject.
    files: ['e2e/**/*.ts', 'test/**/*.ts', '*.ts'],
    rules: {
      'react-hooks/rules-of-hooks': 'off',
    },
  },
];
