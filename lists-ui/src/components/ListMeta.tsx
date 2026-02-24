import { useMemo, useState } from 'react';
import {
  Anchor,
  Autocomplete,
  Button,
  Center,
  ComboboxItem,
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
import { ExternalLinkIcon } from '@atlasoflivingaustralia/ala-mantine';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { FormattedMessage, useIntl } from 'react-intl';

import { Constraint, SpeciesList, SpeciesListSubmit } from '#/api';
import { useConstraints } from '#/api/graphql/useConstraints';
import { listFlags } from '#/helpers';
import { ALAContextProps } from '#/helpers/context/ALAContext';
import { generateCCLink } from '#/helpers/utils/generateCCLinks';
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
  tags: list?.tags || [] || null,
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
  const { constraints, loaded } = useConstraints(ala);
  const [tagSearch, setTagSearch] = useState('');
  const [customTags, setCustomTags] = useState<string[]>([]);

  const intl = useIntl();
  const chooserUrl = "https://creativecommons.org/share-your-work/cclicenses/";

  // Custom validator for the `listType` and `licence` fields.
  const notEmpty = (value: string) =>
    !value || value.trim().length === 0
      ? intl.formatMessage({ id: 'listmeta.validation.notEmpty', defaultMessage: 'Please select a value' })
      : null;

  // Custom validator for the `wkt` field. Checks for the basic WKT structure:
  // a keyword (e.g., POLYGON) followed by parentheses.
  const validWKT = (value: string) =>
    /^\s*\w+\s*\([\s\S]*\)\s*$/i.test(value)
      ? null
      : intl.formatMessage({ id: 'listmeta.validation.wkt', defaultMessage: 'Please enter valid WKT format (e.g., POLYGON(...))' });

  // Form hook
  const form = useForm({
    initialValues: defaultList(initialValues, initialTitle),
    validate: {
      wkt: (value) => (value.length > 0 ? validWKT(value) : null),
      listType: notEmpty,
      licence: notEmpty,
    },
  });

  const regionLabel = useMemo(() => {
    if (!form.values.region) return '';
    const option = constraints?.region?.find((item: Constraint) => item.value === form.values.region);
    return option ? option.label : form.values.region;
  }, [form.values.region, constraints?.region]);

  const handleRegionChange = (selectedValueOrTypedText: string) => {
    const data = constraints?.region || [];
    const selectedOption = data.find((item: Constraint) => item.label === selectedValueOrTypedText);

    if (selectedOption) {
      form.setFieldValue('region', selectedOption.value);
    } else {
      form.setFieldValue('region', selectedValueOrTypedText);
    }
  };

  const handleTagsChange = (selectedValues: string[]) => {
    const data = constraints?.tags || [];

    if (selectedValues.length === 0) {
      form.setFieldValue('tags', null);
    } else {
      const mappedValues = selectedValues.map(label => {
        const option = data.find((item: Constraint) => item.label === label);
        return option ? option.value : label;
      });
      form.setFieldValue('tags', mappedValues);
    }
  };

  const tagOptions = useMemo(() => {
    const base = constraints?.tags || [];
    const custom = customTags.map((v) => ({ value: v, label: v }));
    const selected = (form.values.tags || [])
      .filter((v) => !base.some((b: Constraint) => b.value === v) && !customTags.includes(v))
      .map((v) => ({ value: v, label: v }));

    return [...base, ...custom, ...selected];
  }, [constraints?.tags, customTags, form.values.tags]);

  const commitTag = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return;

    const exists = tagOptions.some((t) => t.value === trimmed);
    if (!exists) setCustomTags((prev) => [...prev, trimmed]);

    const next = Array.from(new Set([...(form.values.tags || []), trimmed]));
    form.setFieldValue('tags', next);
    setTagSearch('');
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
    []
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
              <FormattedMessage
                id="listmeta.licence.label"
                defaultMessage="Licence: <chooserLink>Creative Commons Chooser</chooserLink>"
                values={{
                  chooserLink: (chunks: React.ReactNode) => (
                    <a 
                      href={chooserUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      style={{ textDecoration: 'underline', color: 'blue' }} // Optional styling
                    >
                      {chunks}
                    </a>
                  ),
                }}
              />
            }
            data={constraints?.licence || []}
            placeholder={intl.formatMessage({ id: 'listmeta.licence.placeholder', defaultMessage: 'List licence' })}
            required
            disabled={!loaded || loading}
            renderOption={({ option }: { option: ComboboxItem }) => (
              <Group justify='space-between' wrap='nowrap' style={{ flex: 1 }}>
                <span>{option.label}</span>
                <Anchor
                  href={generateCCLink(option.value)}
                  target='_blank'
                  rel='noopener noreferrer'
                  size='xs'
                  onClick={(e) => e.stopPropagation()}
                >
                  <ExternalLinkIcon />
                </Anchor>
              </Group>
            )}
            {...form.getInputProps('licence')}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 12, md: 12 }}>
          <Textarea
            name='description'
            label={
              <FormattedMessage id="listmeta.description.label" defaultMessage="Description" />
            }
            placeholder={intl.formatMessage({ id: 'listmeta.description.placeholder', defaultMessage: 'List details' })}
            required
            disabled={loading}
            rows={3}
            {...form.getInputProps('description')}
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
            name='tags'
            label={
              <FormattedMessage id="listmeta.tags.label" defaultMessage="Tags" />
            }
            placeholder={intl.formatMessage({ id: 'listmeta.tags.placeholder', defaultMessage: 'Select OR type a custom value' })}
            clearable
            searchable
            radius='md'
            data={tagOptions}
            disabled={!loaded || loading}
            value={form.values.tags || []}
            searchValue={tagSearch}
            onSearchChange={setTagSearch}
            onChange={handleTagsChange}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                commitTag(tagSearch);
              }
            }}
            onBlur={() => commitTag(tagSearch)}
            error={form.errors.tags}
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
        {filteredFlags.map(({ flag, admin, ...props }) => (
          <Grid.Col key={flag} span={{ base: 12, xs: 12, sm: 6, md: 4, lg: 3 }}>
            <FlagCard
              key={form.key(flag)}
              onClick={() =>
                form.setFieldValue(flag, !form.values[flag as keyof SpeciesListSubmit])
              }
              flag={flag}
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
              {initialValues
                ? intl.formatMessage({ id: 'listmeta.button.update.label', defaultMessage: 'Update' })
                : intl.formatMessage({ id: 'listmeta.button.create.label', defaultMessage: 'Create' })}
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