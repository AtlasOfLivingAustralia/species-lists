import { request } from './query';

export default (token: string) => ({
  reindex: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_REDINDEX, 'GET', null, token),
  rematch: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_REMATCH, 'GET', null, token),
  migrate: async (target: 'all' | 'authoritative'): Promise<void> =>
    request(
      `${import.meta.env.VITE_API_ADMIN_MIGRATE}/${target}`,
      'GET',
      null,
      token
    ),
  wipe: async (target: 'index' | 'docs'): Promise<void> =>
    request(
      `${import.meta.env.VITE_API_ADMIN_WIPE}/${target}`,
      'DELETE',
      null,
      token
    ),
});
