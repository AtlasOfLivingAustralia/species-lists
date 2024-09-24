import { SpeciesList } from '#/api';
import {
  Anchor,
  Container,
  Breadcrumbs as Base,
  Text,
  Group,
} from '@mantine/core';
import { Link, useLocation, useRouteLoaderData } from 'react-router-dom';

import classes from './Breadcrumbs.module.css';
import { ChevronRightIcon } from '@atlasoflivingaustralia/ala-mantine';
import { UploadButton } from '#/components/UploadButton';

interface ListLoaderData {
  meta: SpeciesList;
}

const capitalize = (input?: string) =>
  `${(input || '').charAt(0).toUpperCase()}${(input || '').slice(1)}`;

export function Breadcrumbs() {
  const { meta } = (useRouteLoaderData('list') as ListLoaderData) || {};
  const { pathname } = useLocation();

  const parts = pathname.split('/').slice(1);
  const isNested = parts.length > 1 || parts[0] !== '';

  return (
    <Container pt='lg' mb='xl' fluid>
      <Group justify='space-between'>
        <Base
          className={classes.breadcrumbs}
          separator={<ChevronRightIcon size={12} />}
          separatorMargin={5}
        >
          <Anchor href='https://ala.org.au' className={classes.link} size='sm'>
            Home
          </Anchor>
          {isNested ? (
            <Anchor component={Link} to='/' className={classes.link} size='sm'>
              Species lists
            </Anchor>
          ) : (
            <Text size='sm'>Species lists</Text>
          )}
          {isNested && (
            <Text size='sm' truncate='end'>
              {meta ? meta.title : capitalize(parts.pop())}
            </Text>
          )}
        </Base>
        <UploadButton />
      </Group>
    </Container>
  );
}
