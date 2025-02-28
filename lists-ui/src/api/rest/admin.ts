import { MigrateProgress } from '../graphql/types';
import { request } from './query';

export default (token: string) => ({
  reindex: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_REDINDEX, 'GET', null, token),
  rematch: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_REMATCH, 'GET', null, token),
  migrate: async (): Promise<void> => {
    request(
      import.meta.env.VITE_API_ADMIN_MIGRATE,
      'GET',
      null,
      token
    );
  },
  migrateCustom: async (query?: string): Promise<void> => {
    request(
      `${import.meta.env.VITE_API_ADMIN_MIGRATE}/custom`,
      'POST',
      JSON.stringify({ query }),
      token,
      { 'Content-Type': 'application/json' }
    );
  },
  migrateProgress: async (): Promise<MigrateProgress | null> => {
    const progress = await request(
      import.meta.env.VITE_API_ADMIN_MIGRATE_PROGRESS,
      'GET',
      null,
      token
    );

    return progress !== '' ? (progress as MigrateProgress) : null;
  },
  migrateUserdetails: async (): Promise<MigrateProgress | null> => {
    const progress = await request(
      import.meta.env.VITE_API_ADMIN_MIGRATE_USERDETAILS,
      'GET',
      null,
      token
    );

    return progress !== '' ? (progress as MigrateProgress) : null;
  },
  wipe: async (target: 'index' | 'docs'): Promise<void> =>
    request(
      `${import.meta.env.VITE_API_ADMIN_WIPE}/${target}`,
      'DELETE',
      null,
      token
    ),
  reboot: async (): Promise<void> =>
    request(import.meta.env.VITE_API_ADMIN_REBOOT, 'POST', null, token),
  indexes: async (): Promise<unknown> =>
    request(import.meta.env.VITE_API_ADMIN_INDEXES, 'GET', null, token),
});
