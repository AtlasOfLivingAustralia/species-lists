/* eslint-disable @typescript-eslint/no-explicit-any, react-hooks/exhaustive-deps */

import { SpeciesList, SpeciesListConstraints, SpeciesListSubmit } from '#/api';
import { ExternalLinkIcon } from '@atlasoflivingaustralia/ala-mantine';
import {
  Anchor,
  Autocomplete,
  Button,
  Center,
  Divider,
  Grid,
  Group,
  MultiSelect,
  SegmentedControl,
  Select,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMounted } from '@mantine/hooks';
import { useEffect, useMemo, useState } from 'react';

import { listFlags } from '#/helpers';
import { ALAContextProps } from '#/helpers/context/ALAContext';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { FormattedMessage, useIntl } from 'react-intl';
import { FlagCard } from './FlagCard';

interface ListMetaProps {
  ala: ALAContextProps;
  loading?: boolean;
  initialValues?: SpeciesList;
  initialTitle?: string;
  onReset?: () => void;
  onSubmit: (list: SpeciesListSubmit) => void;
}

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
  // State to manage form constraints and region label
  const [constraints, setConstraints] = useState<SpeciesListConstraints | null>(null);
  const [regionLabel, setRegionLabel] = useState('');

  const loaded = Boolean(constraints);
  const mounted = useMounted();
  const intl = useIntl();

  // Custom validator for the `listType` and `licence` fields.
  const notEmpty = (value: string) =>
    !value || value.trim().length === 0 
        ? intl.formatMessage({id:'listmeta.validation.notEmpty', defaultMessage:'Please select a value'}) 
        : null;

  // Custom validator for the `wkt` field.
  const validGeoJSON = (value: string) =>
    /-?(?:\.\d+|\d+(?:\.\d*)?)/.test(value) 
        ? null 
        : intl.formatMessage({id:'listmeta.validation.geojson', defaultMessage:'Please enter valid GeoJSON'});


  // Form hook
  const form = useForm({
    initialValues: defaultList(initialValues, initialTitle),
    validate: {
      wkt: (value) => (value.length > 0 ? validGeoJSON(value) : null),
      listType: notEmpty,
      licence: notEmpty,
    },
  });

  // Retrieve dynamic values (constraints for selects/autocomplete)
  useEffect(() => {
    async function fetchConstraints() {
      try {
        setConstraints(await ala.rest.lists.constraints());
      } catch (error) {
        console.error("Error fetching constraints:", error);
      }
    }

    if (mounted) fetchConstraints();
  }, [mounted]);

  // Effect to set the initial regionLabel based on the form's region value.
  // This ensures that if `form.values.region` has a value (e.g., 'VIC'),
  // the Autocomplete displays its corresponding label ('Victoria') on load.
  useEffect(() => {
    if (form.values.region) {
      const option = constraints?.region?.find(item => item.value === form.values.region);
      
      if (option) {
        // If an option with matching value is found in constraints, display its label.
        setRegionLabel(option.label);
      } else {
        // If form.values.region is a custom value not in the constraints list,
        // display the value as is.
        setRegionLabel(form.values.region);
      }
    } else {
      // If form.values.region is empty, ensure regionLabel is also empty.
      setRegionLabel('');
    }
  }, [form.values.region, constraints?.region]); 

  const handleRegionChange = (selectedValueOrTypedText: string) => {
    const data = constraints?.region || [];
    setRegionLabel(selectedValueOrTypedText);
    const selectedOption = data.find(item => item.label === selectedValueOrTypedText);

    if (selectedOption) {
      // If a matching option was found (meaning the user selected from the dropdown),
      // update the form's `region` field with the actual `value` (e.g., 'NSW').
      form.setFieldValue('region', selectedOption.value);
    } else {
      // If no matching option was found (user typed a custom value or cleared the input),
      // update the form's `region` field with the typed string.
      form.setFieldValue('region', selectedValueOrTypedText);
    }
  };

  const handleSumbit = (values: typeof form.values) => {
    onSubmit(values);
  };

  const filteredFlags = useMemo(
    () => listFlags.filter(({ admin }) => ala.isAdmin || !admin),
    [ala.isAdmin]
  );

  const visibilityLabels = useMemo(
    () => [
      {
        value: 'public',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faEye} fontSize={14} />
            <span><FormattedMessage id='listmeta.public.label' defaultMessage='Public' /></span>
          </Center>
        ),
      },
      {
        value: 'private',
        label: (
          <Center style={{ gap: 10 }}>
            <FontAwesomeIcon icon={faEyeSlash} fontSize={14} />
            <span><FormattedMessage id='listmeta.private.label' defaultMessage='Private' /></span>
          </Center>
        ),
      },
    ],
    [ala.isAdmin]
  );


  return (
    <form onSubmit={form.onSubmit(handleSumbit)}>
      <Grid>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <TextInput
            name='title'
            label={
              <FormattedMessage id="listmeta.title.label" defaultMessage="Name" />
            }
            placeholder={intl.formatMessage({ id: 'listmeta.title.placeholder', defaultMessage: 'List name' })}
            required
            disabled={loading}
            {...form.getInputProps('title')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <TextInput
            name='description'
            label={
              <FormattedMessage id="listmeta.description.label" defaultMessage="Description" />
            }
            placeholder={intl.formatMessage({ id: 'listmeta.description.placeholder', defaultMessage: 'List details' })}
            required
            disabled={loading}
            {...form.getInputProps('description')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <Select
            name='listType'
            label={
              <FormattedMessage id="listmeta.type.label" defaultMessage="Type" />
            }
            data={constraints?.listType || []}
            placeholder={intl.formatMessage({ id: 'listmeta.type.placeholder', defaultMessage: 'List type' })}
            required
            disabled={!loaded || loading}
            error={form.errors.listType}
            {...form.getInputProps('listType')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <Select
            name='licence'
            label={
              <FormattedMessage id="listmeta.licence.label" defaultMessage="Licence" />
            }
            data={constraints?.licence || []}
            placeholder={intl.formatMessage({ id: 'listmeta.licence.placeholder', defaultMessage: 'List licence' })}
            required
            disabled={!loaded || loading}
            {...form.getInputProps('licence')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <TextInput
            name='authority'
            label={
              <FormattedMessage id="listmeta.authority.label" defaultMessage="Authority" />
            }
            placeholder={intl.formatMessage({ id: 'listmeta.authority.placeholder', defaultMessage: 'Authority' })}
            disabled={loading}
            {...form.getInputProps('authority')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <Autocomplete
            name='region'
            label={
              <FormattedMessage id="listmeta.region.label" defaultMessage="Region" />
            }
            placeholder={intl.formatMessage({ id: 'listmeta.region.placeholder', defaultMessage: 'Select OR type a custom value' })}
            clearable
            radius='md'
            data={constraints?.region || []} 
            disabled={!loaded || loading}
            value={regionLabel} 
            onChange={handleRegionChange} 
            onBlur={form.getInputProps('region').onBlur}
            error={form.errors.region}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <MultiSelect
            label={
              <FormattedMessage id="listmeta.tags.label" defaultMessage="Tags" />
            }
            data={['biocollect', 'galah', 'spatial-portal', 'profiles', 'arga']}
            placeholder={
              (form.values['tags'] || []).length > 0
          ? undefined
          : intl.formatMessage({ id: 'listmeta.tags.placeholder', defaultMessage: 'Select tags' })
            }
            disabled={loading}
            {...form.getInputProps('tags')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
          <Text size="sm" fw={500} mt={3} mb={1}>
            <FormattedMessage id="listmeta.visibility.label" defaultMessage="Visibility" />
          </Text>
          <SegmentedControl
            disabled={loading}
            radius='md'
            data={visibilityLabels}
            value={form.values.isPrivate ? 'private' : 'public'}
            onChange={(value) => form.setFieldValue('isPrivate', value === 'private')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12 }}>
          <Textarea
            name='wkt'
            label={
              <>
          <FormattedMessage id="listmeta.wkt.label" defaultMessage="Geo Coordinates" /> (
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
            placeholder={intl.formatMessage({ id: 'listmeta.wkt.placeholder', defaultMessage: 'Enter geo coordinates' })}
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
              { initialValues 
                  ? intl.formatMessage({ id: 'listmeta.button.update.label', defaultMessage: 'Update'})
                  : intl.formatMessage({ id: 'listmeta.button.create.label', defaultMessage: 'Create'})
              }
            </Button>
            <Button
              radius='md'
              variant='light'
              onClick={() => (onReset ? onReset() : form.reset())}
              disabled={loading}
            >
              <FormattedMessage id='listmeta.button.cancel.label' defaultMessage='Cancel' />
            </Button>
          </Group>
        </Grid.Col>
      </Grid>
    </form>
  );
}