import { KV } from '#/api';
import { createParser } from 'nuqs';

export default createParser({
  parse(query): KV[] | null {
    const parts = query.split(',');

    if (parts.length === 0) return null;

    const decodedParts = parts.map((part) => part.split(':'));

    if (decodedParts.find((part) => part.length !== 2)) return null;

    return decodedParts.map(([key, value]) => ({ key, value }));
  },
  serialize(value: KV[]): string {
    return value.map(({ key, value }) => `${key}:${value}`).join(',');
  },
});
