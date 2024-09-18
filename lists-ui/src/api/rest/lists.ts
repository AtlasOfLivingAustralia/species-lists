import { request } from './query';

import {
  SpeciesList,
  SpeciesListConstraints,
  SpeciesListSubmit,
  UploadResult,
} from '../graphql/types';
import { FileWithPath } from '@mantine/dropzone';

export default (token: string) => ({
  delete: async (id: string): Promise<void> =>
    request(
      `${import.meta.env.VITE_API_LIST_DELETE}/${id}`,
      'DELETE',
      null,
      token
    ),
  rematch: async (id: string): Promise<void> =>
    request(
      `${import.meta.env.VITE_API_LIST_REMATCH}/${id}`,
      'GET',
      null,
      token
    ),
  reindex: async (id: string): Promise<void> =>
    request(
      `${import.meta.env.VITE_API_LIST_REINDEX}/${id}`,
      'GET',
      null,
      token
    ),
  upload: async (files: FileWithPath[]): Promise<UploadResult> => {
    // Only upload the first file
    const [file] = files;

    // Create a new FormData object & add the file to it
    const form = new FormData();
    form.append('file', file);

    // First the request
    return request(import.meta.env.VITE_API_LIST_UPLOAD, 'POST', form, token);
  },
  ingest: async (
    list: SpeciesListSubmit,
    file: string
  ): Promise<SpeciesList> => {
    // Create a new FormData object
    const form = new FormData();

    // Add the list properties to it
    form.append('title', list.title);
    form.append('description', list.description);
    form.append('listType', list.listType);
    form.append('region', list.region);
    form.append('authority', list.authority);
    form.append('licence', list.licence);
    form.append('isPrivate', list.isPrivate.toString());
    form.append('isAuthoritative', list.isAuthoritative.toString());
    form.append('isBIE', list.isBIE.toString());
    form.append('isThreatened', list.isThreatened.toString());
    form.append('isInvasive', list.isInvasive.toString());
    form.append('isSDS', list.isSDS?.toString());
    form.append('tags', list.tags?.toString());
    form.append('file', file);

    // First the request
    return request(import.meta.env.VITE_API_LIST_INGEST, 'POST', form, token);
  },
  constraints: async (
    type?: 'lists' | 'licenses' | 'countries'
  ): Promise<SpeciesListConstraints> =>
    request(
      type
        ? `${import.meta.env.VITE_API_LIST_CONSTRAINTS}/${type}`
        : import.meta.env.VITE_API_LIST_CONSTRAINTS,
      'GET',
      null
    ),
});
