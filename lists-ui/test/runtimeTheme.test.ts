import assert from 'node:assert/strict';
import { afterEach, describe, it, mock } from 'node:test';
import {
  cleanMustache,
  injectFathom,
  injectThemeCss,
  injectThemeJs,
  primaryColorOverride,
  setFavicon,
  splitUrlList,
  type ThemeDocument,
} from '../src/helpers/utils/runtimeTheme.ts';

interface FakeElement {
  tagName: string;
  attrs: Record<string, string>;
  rel?: string;
  href?: string;
  src?: string;
  defer?: boolean;
  onload?: () => void;
  onerror?: () => void;
  setAttribute(name: string, value: string): void;
  removeAttribute(name: string): void;
}

/** Just enough of `document` for the helpers: elements appended to <head> and <body>, in order. */
function fakeDocument(icon?: FakeElement) {
  const head: FakeElement[] = [];
  const body: FakeElement[] = [];
  const doc = {
    createElement: (tagName: string): FakeElement => ({
      tagName,
      attrs: {},
      setAttribute(name, value) {
        this.attrs[name] = value;
      },
      removeAttribute(name) {
        delete this.attrs[name];
      },
    }),
    head: { appendChild: (el: FakeElement) => head.push(el) },
    body: { appendChild: (el: FakeElement) => body.push(el) },
    querySelector: () => icon ?? null,
  };
  return { doc: doc as unknown as ThemeDocument, head, body };
}

describe('cleanMustache', () => {
  const template =
    '<div class="{{containerClass}} banner">' +
    '<form action="{{searchServer}}{{searchPath}}"></form>' +
    '<div class="{{loginStatus}}"><a href="{{loginURL}}" class="loginBtn">Login</a></div>' +
    '</div>';

  it('substitutes the container class and the search URL', () => {
    const out = cleanMustache(template, 'container', 'https://search.example.org');
    assert.match(out, /class="container banner"/);
    assert.match(out, /action="https:\/\/search\.example\.org"/);
  });

  it('strips the braces of every other tag, keeping its name, as atlas-index does', () => {
    const out = cleanMustache(template, 'container-fluid', 'https://search.example.org');
    assert.doesNotMatch(out, /\{\{|}}/);
    assert.match(out, /class="loginStatus"/);
    assert.match(out, /href="loginURL"/);
  });

  it('leaves the search placeholder as bare names when no search URL is given', () => {
    const out = cleanMustache(template, 'container-fluid');
    assert.match(out, /action="searchServersearchPath"/);
    assert.doesNotMatch(out, /\{\{|}}/);
  });
});

describe('splitUrlList', () => {
  it('splits, trims and drops empty entries', () => {
    assert.deepEqual(splitUrlList(' /a.css , ,/b.css,'), ['/a.css', '/b.css']);
    assert.deepEqual(splitUrlList(undefined), []);
    assert.deepEqual(splitUrlList(''), []);
  });
});

describe('injectThemeCss', () => {
  it('appends one stylesheet per URL to <head>, in the order given, so the last one wins', () => {
    const { doc, head } = fakeDocument();
    injectThemeCss('/theme/base.css, /theme/override.css', doc);
    assert.deepEqual(
      head.map((el) => [el.tagName, el.rel, el.href]),
      [
        ['link', 'stylesheet', '/theme/base.css'],
        ['link', 'stylesheet', '/theme/override.css'],
      ]
    );
  });

  it('adds nothing when unset', () => {
    const { doc, head } = fakeDocument();
    injectThemeCss('', doc);
    injectThemeCss(undefined, doc);
    assert.equal(head.length, 0);
  });
});

describe('injectThemeJs', () => {
  it('loads scripts one after another, even when one fails', () => {
    const { doc, body } = fakeDocument();
    injectThemeJs('/a.js,/b.js,/c.js', doc);
    assert.deepEqual(body.map((el) => el.src), ['/a.js']);
    body[0].onerror?.();
    assert.deepEqual(body.map((el) => el.src), ['/a.js', '/b.js']);
    body[1].onload?.();
    assert.deepEqual(body.map((el) => el.src), ['/a.js', '/b.js', '/c.js']);
  });
});

describe('injectFathom', () => {
  it('loads Fathom with the site id', () => {
    const { doc, head } = fakeDocument();
    injectFathom('ABCDEF', doc);
    assert.equal(head.length, 1);
    assert.equal(head[0].src, 'https://cdn.usefathom.com/script.js');
    assert.equal(head[0].defer, true);
    assert.deepEqual(head[0].attrs, { 'data-site': 'ABCDEF', 'data-spa': 'auto' });
  });

  it('loads nothing without a site id, as in the community build', () => {
    const { doc, head } = fakeDocument();
    injectFathom('', doc);
    injectFathom('  ', doc);
    injectFathom(undefined, doc);
    assert.equal(head.length, 0);
  });
});

describe('setFavicon', () => {
  it('repoints the existing icon', () => {
    const icon = fakeDocument().doc.createElement('link') as unknown as FakeElement;
    icon.setAttribute('type', 'image/svg+xml');
    const { doc, head } = fakeDocument(icon);
    setFavicon('/theme/favicon.png', doc);
    assert.equal(icon.href, '/theme/favicon.png');
    assert.equal(icon.attrs.type, undefined);
    assert.equal(head.length, 0);
  });

  it('does nothing when unset', () => {
    const { doc, head } = fakeDocument();
    setFavicon('', doc);
    assert.equal(head.length, 0);
  });
});

describe('primaryColorOverride', () => {
  const names = ['rust', 'violet', 'teal'];

  afterEach(() => mock.restoreAll());

  it('keeps the build theme when unset', () => {
    assert.equal(primaryColorOverride(undefined, names), null);
    assert.equal(primaryColorOverride(' ', names), null);
  });

  it('uses a palette the theme already has', () => {
    assert.deepEqual(primaryColorOverride('violet', names), { primaryColor: 'violet' });
  });

  it('builds a palette from a hex colour', () => {
    const override = primaryColorOverride('#6b2fb3', names);
    assert.equal(override?.primaryColor, 'portal');
    assert.equal(override?.colors?.portal.length, 10);
    assert.ok(override?.colors?.portal.every((c) => c === '#6b2fb3'));
  });

  it('ignores an unknown colour with a warning instead of breaking the app', () => {
    const warn = mock.method(console, 'warn', () => {});
    assert.equal(primaryColorOverride('purple-ish', names), null);
    assert.equal(warn.mock.callCount(), 1);
  });
});
