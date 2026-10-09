import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';
import { getAppConfigValue, getRuntimeConfig } from '../src/helpers/utils/runtimeConfig.ts';

const g = globalThis as unknown as { window?: unknown };

afterEach(() => {
  delete g.window;
});

describe('getAppConfigValue', () => {
  it('returns the build-time fallback when no window exists', () => {
    assert.deepEqual(getRuntimeConfig(), {});
    assert.equal(getAppConfigValue('VITE_API_BASEURL', 'build-time'), 'build-time');
  });

  it('returns the build-time fallback when neither config file set the key', () => {
    g.window = { APP_CONFIG: {}, APP_CONFIG_LOCAL: undefined };
    assert.equal(getAppConfigValue('VITE_API_BASEURL', 'build-time'), 'build-time');
  });

  it('uses config.js defaults over the build-time value', () => {
    g.window = { APP_CONFIG: { VITE_API_BASEURL: 'https://default.example.org' } };
    assert.equal(getAppConfigValue('VITE_API_BASEURL', 'build-time'), 'https://default.example.org');
  });

  it('lets config.local.js win over config.js, key by key', () => {
    g.window = {
      APP_CONFIG: {
        VITE_API_BASEURL: 'https://default.example.org',
        VITE_AUTH_CLIENT_ID: 'default-client',
      },
      APP_CONFIG_LOCAL: { VITE_API_BASEURL: 'https://local.example.org' },
    };
    assert.equal(getAppConfigValue('VITE_API_BASEURL', 'build-time'), 'https://local.example.org');
    assert.equal(getAppConfigValue('VITE_AUTH_CLIENT_ID', 'build-time'), 'default-client');
    assert.equal(getAppConfigValue('VITE_AUTH_SCOPE', 'build-time'), 'build-time');
  });

  it('works for any VITE_* key, with no list to maintain', () => {
    g.window = { APP_CONFIG_LOCAL: { VITE_ALA_HOME_PAGE: 'https://portal.example.org' } };
    assert.equal(getAppConfigValue('VITE_ALA_HOME_PAGE', 'https://ala.org.au'), 'https://portal.example.org');
  });

  it('treats a blank value as not set', () => {
    g.window = { APP_CONFIG_LOCAL: { VITE_API_BASEURL: '   ' } };
    assert.equal(getAppConfigValue('VITE_API_BASEURL', 'build-time'), 'build-time');
  });
});
