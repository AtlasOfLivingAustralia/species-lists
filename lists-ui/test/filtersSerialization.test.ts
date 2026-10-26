import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import parseAsFilters, { serializeFilters } from '../src/helpers/utils/parseAsFilters.ts';

describe('serializeFilters', () => {
  it('serialises filters to key:value pairs joined by commas', () => {
    assert.equal(
      serializeFilters([
        { key: 'classification.kingdom', value: 'Animalia' },
        { key: 'habit', value: 'tree' },
      ]),
      'classification.kingdom:Animalia,habit:tree'
    );
  });

  it('round-trips through the nuqs parser', () => {
    const filters = [
      { key: 'classification.kingdom', value: 'Animalia' },
      { key: 'classification.family', value: 'Fabaceae' },
      { key: 'habit', value: 'shrub' },
    ];

    assert.deepEqual(parseAsFilters.parse(serializeFilters(filters)), filters);
  });

  it('round-trips a single filter with spaces encoded in the URL query', () => {
    const filters = [{ key: 'classification.kingdom', value: 'Plantae' }];

    // nuqs hands the parser the encoded query segment
    const query = encodeURIComponent(serializeFilters(filters));
    assert.deepEqual(parseAsFilters.parse(decodeURIComponent(query)), filters);
  });

  it('serialises an empty list to an empty string', () => {
    assert.equal(serializeFilters([]), '');
  });
});
