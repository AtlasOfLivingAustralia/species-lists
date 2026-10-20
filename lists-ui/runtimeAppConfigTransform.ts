/**
 * Pure source rewrite behind viteRuntimeConfigPlugin's community build, kept free of any Vite
 * import so it can be unit tested directly.
 *
 * `import.meta.env.VITE_X` becomes `__getAppConfigValue('VITE_X', import.meta.env.VITE_X)`, so
 * Vite still inlines the build's own value as the fallback while config.js / config.local.js can
 * override it (see src/helpers/utils/runtimeConfig.ts).
 *
 * Sources are parsed with the TypeScript compiler rather than matched as text, so only real
 * `import.meta.env` expressions are touched: the same text inside a string, a comment or the
 * literal part of a template stays as it was.
 */
import ts from 'typescript';

/** Alias used for the injected import, so it cannot clash with a name the file already imports. */
const HELPER = '__getAppConfigValue';
const APP_KEY_PREFIX = 'VITE_';

interface Analysis {
  /** Each `import.meta.env.VITE_*` read to rewrite, in source order. */
  reads: { start: number; end: number; key: string }[];
  /** Each use that cannot be rewritten, so its key would silently ignore config.js. */
  unsupported: string[];
}

function scriptKindFor(fileName: string): ts.ScriptKind {
  if (fileName.endsWith('.tsx')) return ts.ScriptKind.TSX;
  if (fileName.endsWith('.jsx')) return ts.ScriptKind.JSX;
  if (/\.[cm]?js$/.test(fileName)) return ts.ScriptKind.JS;
  return ts.ScriptKind.TS;
}

/** `import.meta.env`, and not `foo.env` or `import.meta.other`. */
function isImportMetaEnv(node: ts.Node): node is ts.PropertyAccessExpression {
  return (
    ts.isPropertyAccessExpression(node) &&
    node.name.text === 'env' &&
    ts.isMetaProperty(node.expression) &&
    node.expression.keywordToken === ts.SyntaxKind.ImportKeyword &&
    node.expression.name.text === 'meta'
  );
}

/** `expr = …`, `expr += …`, `expr++`, `delete expr`: rewriting `expr` into a call would break these. */
function isWriteTarget(expr: ts.Expression): boolean {
  const parent = expr.parent;
  if (ts.isBinaryExpression(parent)) {
    const op = parent.operatorToken.kind;
    return (
      parent.left === expr &&
      op >= ts.SyntaxKind.FirstAssignment &&
      op <= ts.SyntaxKind.LastAssignment
    );
  }
  if (ts.isPrefixUnaryExpression(parent) || ts.isPostfixUnaryExpression(parent)) {
    return parent.operator === ts.SyntaxKind.PlusPlusToken || parent.operator === ts.SyntaxKind.MinusMinusToken;
  }
  return ts.isDeleteExpression(parent);
}

function analyse(code: string, fileName: string): Analysis {
  const result: Analysis = { reads: [], unsupported: [] };
  if (!code.includes('import.meta')) return result;
  const sf = ts.createSourceFile(fileName, code, ts.ScriptTarget.Latest, true, scriptKindFor(fileName));

  const flag = (node: ts.Node, what: string) => {
    const { line } = sf.getLineAndCharacterOfPosition(node.getStart(sf));
    result.unsupported.push(`${what} (line ${line + 1})`);
  };

  const visit = (node: ts.Node): void => {
    if (isImportMetaEnv(node)) {
      const parent = node.parent;
      if (ts.isPropertyAccessExpression(parent) && parent.expression === node) {
        const key = parent.name.text;
        // DEV, MODE, BASE_URL… are not deployment settings, so they keep Vite's own handling
        if (key.startsWith(APP_KEY_PREFIX)) {
          if (parent.questionDotToken || node.questionDotToken) {
            flag(parent, `optional chaining: import.meta.env?.${key}`);
          } else if (isWriteTarget(parent)) {
            flag(parent, `assignment to import.meta.env.${key}`);
          } else {
            result.reads.push({ start: parent.getStart(sf), end: parent.end, key });
          }
        }
      } else if (ts.isElementAccessExpression(parent) && parent.expression === node) {
        flag(node, 'computed access: import.meta.env[...]');
      } else if (
        ts.isVariableDeclaration(parent) &&
        parent.initializer === node &&
        ts.isObjectBindingPattern(parent.name)
      ) {
        flag(node, 'destructuring: const { VITE_... } = import.meta.env');
      } else {
        // spread, return, call argument, assignment, `in`…: reads through the object never reach
        // getAppConfigValue
        flag(node, 'import.meta.env used as a whole object');
      }
      return;
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return result;
}

/**
 * Rewrite every `import.meta.env.VITE_*` read in `code`, and prepend the import of the helper from
 * `helperPath` on the SAME first line, so line numbers in stack traces stay as they were. Returns
 * null when the file has no such read, so the caller can leave it untouched. `fileName` picks the
 * parser (TS, TSX, JS or JSX).
 */
export function rewriteAppEnvReferences(code: string, helperPath: string, fileName: string): string | null {
  const { reads } = analyse(code, fileName);
  if (reads.length === 0) return null;
  let rewritten = code;
  // last to first, so the offsets of the reads not yet rewritten stay valid
  for (const { start, end, key } of [...reads].reverse()) {
    const original = code.slice(start, end);
    rewritten = `${rewritten.slice(0, start)}${HELPER}('${key}', ${original})${rewritten.slice(end)}`;
  }
  return `import { getAppConfigValue as ${HELPER} } from ${JSON.stringify(helperPath)};${rewritten}`;
}

/**
 * Uses of `import.meta.env` that `rewriteAppEnvReferences` cannot rewrite, so a `VITE_*` read that
 * way would silently ignore config.js. The plugin fails the build on any of them, rather than ship
 * a portal where one key cannot be overridden.
 */
export function findUnsupportedAppEnvUsage(code: string, fileName: string): string[] {
  return analyse(code, fileName).unsupported;
}
