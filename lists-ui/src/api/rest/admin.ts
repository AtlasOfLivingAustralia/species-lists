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
  migrateProgress: async (): Promise<MigrateProgress | null> => {
    const progress = await request(
      import.meta.env.VITE_API_ADMIN_MIGRATE_PROGRESS,
      'GET',
      null,
      token
    );

    return progress !== '' ? (progress as MigrateProgress) : null;
  },
  migrateReset: async (): Promise<void> => {
    request(
      `${import.meta.env.VITE_API_ADMIN_MIGRATE}/reset`,
      'POST',
      null,
      token,
    );
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
