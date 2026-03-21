import js from "@eslint/js";
import tseslint from "@typescript-eslint/eslint-plugin";
import tsparser from "@typescript-eslint/parser";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import globals from "globals";

export default [
  // Ignore build output and generated files
  {
    ignores: ["dist/**", "node_modules/**"],
  },

  // Base JS recommended rules
  js.configs.recommended,

  // TypeScript + React source files
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      parser: tsparser,
      parserOptions: {
        ecmaVersion: 2020,
        sourceType: "module",
      },
      globals: {
        ...globals.browser,
      },
    },
    plugins: {
      "@typescript-eslint": tseslint,
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      // TypeScript recommended rules
      ...tseslint.configs.recommended.rules,

      // TypeScript handles undefined checks — disable the JS-only no-undef rule
      // for TS files to avoid false positives on DOM/TS types (HeadersInit, etc.)
      "no-undef": "off",

      // React Hooks rules
      ...reactHooks.configs.recommended.rules,

      // Unused vars: allow underscore-prefixed names as intentional omissions
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          varsIgnorePattern: "^_",
          argsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
          destructuredArrayIgnorePattern: "^_",
        },
      ],

      // React Refresh: warn on non-component exports from component files
      "react-refresh/only-export-components": [
        "warn",
        { allowConstantExport: true },
      ],
    },
  },
];
