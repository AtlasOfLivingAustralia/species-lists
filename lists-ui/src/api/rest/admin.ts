import { request } from './query';

export default (token: string) => ({
  reindex: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_REDINDEX, 'GET', null, token),
  rematch: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_REMATCH, 'GET', null, token),
  migrate: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_MIGRATE, 'GET', null, token),
});
