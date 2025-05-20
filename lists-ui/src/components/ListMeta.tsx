/* eslint-disable @typescript-eslint/no-explicit-any, react-hooks/exhaustive-deps */

import {
  Anchor,
  Autocomplete,
  Button,
  Divider,
  Grid,
  Group,
  MultiSelect,
  Select,
  Textarea,
  TextInput,
} from '@mantine/core';
import { SpeciesList, SpeciesListConstraints, SpeciesListSubmit } from '#/api';
import { useForm } from '@mantine/form';
import { ExternalLinkIcon } from '@atlasoflivingaustralia/ala-mantine';
import { useEffect, useMemo, useState } from 'react';
import { useMounted } from '@mantine/hooks';

import { listFlags } from '#/helpers';
import { FlagCard } from './FlagCard';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { ALAContextProps } from '#/helpers/context/ALAContext';

interface ListMetaProps {
  ala: ALAContextProps;
  loading?: boolean;
  initialValues?: SpeciesList;
  initialTitle?: string;
  onReset?: () => void;
  onSubmit: (list: SpeciesListSubmit) => void;
}

interface RegionOption {
  value: string;
  label: string;
}

const notEmpty = (value: string) =>
  value.length < 2 ? 'Please select a value' : null;

const validGeoJSON = (value: string) =>
  /-?(?:\.\d+|\d+(?:\.\d*)?)/.test(value) ? null : 'Please enter valid GeoJSON';

const defaultList = (
  list?: SpeciesList,
  initialTitle?: string
): SpeciesListSubmit => ({
  authority: list?.authority || '',
  description: list?.description || '',
  isAuthoritative: list?.isAuthoritative || false,
  isInvasive: list?.isInvasive || false,
  isPrivate:
    list?.isPrivate !== undefined && list?.isPrivate !== null
      ? list.isPrivate
      : true,
  isBIE: list?.isBIE || false,
  isSDS: list?.isSDS || false,
  isThreatened: list?.isThreatened || false,
  lastUpdated: list?.lastUpdated || '',
  licence: list?.licence || '',
  listType: list?.listType || '',
  region: list?.region || '',
  title: list?.title || initialTitle || '',
  wkt: list?.wkt || '',
  tags: list?.tags || [],
  dataResourceUid: list?.dataResourceUid || '',
  metadataLastUpdated: list?.metadataLastUpdated || '',
});

export function ListMeta({
  ala,
  loading,
  initialValues,
  initialTitle,
  onReset,
  onSubmit,
}: ListMetaProps) {
  const [constraints, setConstraints] = useState<SpeciesListConstraints | null>(
    null
  );
  const loaded = Boolean(constraints);
  const mounted = useMounted();
  const [regionLabel, setRegionLabel] = useState('')

  // Form hook
  const form = useForm({
    initialValues: defaultList(initialValues, initialTitle),
    validate: {
      wkt: (value) => (value.length > 0 ? validGeoJSON(value) : null),
      listType: notEmpty,
      licence: notEmpty,
    },
  });

  // Hook to retireve dynamic values
  useEffect(() => {
    async function fetchConstraints() {
      try {
        // Retrieve specist list constraints
        setConstraints(await ala.rest.lists.constraints());
      } catch (error) {
        console.error(error);
      }
    }

    if (mounted) fetchConstraints();
  }, [mounted]);

  // Find the region "label" for the initial "value" (if any)
  useEffect(() => {
    if (form.values.region) {
      const option = constraints?.region?.find(item => item.value === form.values.region);
      if (option) {
        setRegionLabel(option.label);
      }
    }
  }, [form.values.region, constraints?.region]);

  const handleRegionChange = (selectedLabel: string) => {
    setRegionLabel(selectedLabel);
    const data = constraints?.region || []
    // Find matching option by label
    const selectedOption = data.find(item => item.label === selectedLabel);
    
    if (selectedOption) {
      // Set the value in the form when it matches an option
      form.setFieldValue('region', selectedOption.value);
    } else {
      // For user-entered values, use the text as both label and value
      form.setFieldValue('region', selectedLabel);
    }
  };


  const handleSumbit = (values: typeof form.values) => {
    onSubmit(values);
  };

  const filteredFlags = useMemo(
    () => listFlags.filter(({ admin }) => ala.isAdmin || !admin),
    [ala.isAdmin]
  );

  return (
    <form onSubmit={form.onSubmit(handleSumbit)}>
      <Grid>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <TextInput
            name='title'
            label='Name'
            placeholder='List name'
            required
            disabled={loading}
            {...form.getInputProps('title')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <TextInput
            name='description'
            label='Description'
            placeholder='List details'
            required
            disabled={loading}
            {...form.getInputProps('description')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <Select
            name='listType'
            label='Type'
            data={constraints?.listType || []}
            placeholder='List type'
            required
            disabled={!loaded || loading}
            {...form.getInputProps('listType')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <Select
            name='licence'
            label='Licence'
            data={constraints?.licence || []}
            placeholder='List licence'
            required
            disabled={!loaded || loading}
            {...form.getInputProps('licence')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <TextInput
            name='authority'
            label='Authority'
            placeholder='Authority'
            disabled={loading}
            {...form.getInputProps('authority')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <Autocomplete
            name='region'
            label="Region"
            placeholder="Choose or type a custom value"
            clearable
            data={constraints?.region || []}
            disabled={!loaded || loading}
            value={regionLabel}
            {...form.getInputProps('region', { onChange: handleRegionChange })}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <MultiSelect
            label='Tags'
            data={['biocollect', 'galah', 'spatial-portal', 'profiles', 'arga']}
            placeholder={
              (form.values['tags'] || []).length > 0 ? undefined : 'Select tags'
            }
            disabled={loading}
            {...form.getInputProps('tags')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12 }}>
          <Textarea
            name='wkt'
            label={
              <>
                Geo Coordinates (
                <Anchor
                  style={{ fontSize: 14 }}
                  fw='bold'
                  target='_blank'
                  href='https://en.wikipedia.org/wiki/Well-known_text_representation_of_geometry'
                >
                  WKT <ExternalLinkIcon />
                </Anchor>
                )
              </>
            }
            placeholder='Enter geo coordinates'
            minRows={3}
            maxRows={8}
            autosize
            disabled={loading}
            {...form.getInputProps('wkt')}
          />
        </Grid.Col>
        <Grid.Col span={12}>
          <Divider variant='dashed' my='md' />
        </Grid.Col>
        <Grid.Col span={{ base: 12, xs: 12, sm: 6, md: 4, lg: 3 }}>
          <FlagCard
            key={form.key('isPrivate')}
            label={form.values.isPrivate ? 'Private' : 'Public'}
            description={
              form.values.isPrivate
                ? 'Hidden from the public'
                : 'Visible to everyone'
            }
            icon={form.values.isPrivate ? faEyeSlash : faEye}
            onClick={() =>
              form.setFieldValue('isPrivate', !form.values.isPrivate)
            }
            disabled={loading}
            {...form.getInputProps('isPrivate', { type: 'checkbox' })}
          />
        </Grid.Col>
        {/* eslint-disable-next-line @typescript-eslint/no-unused-vars */}
        {filteredFlags.map(({ flag, admin, ...props }) => (
          <Grid.Col key={flag} span={{ base: 12, xs: 12, sm: 6, md: 4, lg: 3 }}>
            <FlagCard
              key={form.key(flag)}
              onClick={() =>
                form.setFieldValue(flag, !(form.values as any)[flag])
              }
              disabled={loading}
              {...props}
              {...form.getInputProps(flag, { type: 'checkbox' })}
            />
          </Grid.Col>
        ))}
        <Grid.Col span={{ base: 12 }} pt='lg'>
          <Group justify='center'>
            <Button
              radius='md'
              variant='filled'
              type='submit'
              loading={loading}
            >
              Confirm
            </Button>
            <Button
              radius='md'
              variant='light'
              onClick={() => (onReset ? onReset() : form.reset())}
              disabled={loading}
            >
              Reset
            </Button>
          </Group>
        </Grid.Col>
      </Grid>
    </form>
  );
}
