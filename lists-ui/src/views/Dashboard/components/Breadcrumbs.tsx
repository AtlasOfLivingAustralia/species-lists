import {
  Anchor,
  Breadcrumbs as Base,
  Text,
  Group,
} from '@mantine/core';
import { Link, useLocation } from 'react-router';

import classes from './Breadcrumbs.module.css';
import { ChevronRightIcon } from '@atlasoflivingaustralia/ala-mantine';
import { ActionButtons } from '#/components/ActionButtons';

const capitalize = (input?: string) =>
  `${(input || '').charAt(0).toUpperCase()}${(input || '').slice(1)}`;

interface BreadcrumbsProps {
  listTitle: string | undefined;
}

export function Breadcrumbs({ listTitle }: BreadcrumbsProps) {
  // const { meta } = (useRouteLoaderData('list') as ListLoaderData) || {};
  const { pathname } = useLocation();

  const parts = pathname.split('/').slice(1);
  const isListPage = parts[0] === 'list' && parts.length > 1;
  const isNestedAction = isListPage && parts.length > 2; // e.g., /list/123/edit

    // Determine the text for the last breadcrumb item
  let lastItemText: string | null = null;
  if (listTitle || isListPage) {
    if (listTitle) {
      lastItemText = listTitle; // Use title from state if available
    } else if (!isNestedAction) {
      lastItemText = 'Loading list...'; // Placeholder while loading
    } else {
      lastItemText = capitalize(parts[parts.length -1]); // Use last path part for actions like 'edit'
    }
  }

  return (
    <>
      <Group justify='space-between'>
        <Base
          className={classes.breadcrumbs}
          separator={<ChevronRightIcon size={12} />}
          separatorMargin={5}
        >
          <Anchor href='https://ala.org.au' className={classes.link} size='sm'>
            Home
          </Anchor>
          {parts.length > 0 ?  (
            <Anchor component={Link} to='/' className={classes.link} size='sm'>
              Species lists
            </Anchor>
          ) : (
            <Text size='sm'>Species lists</Text>
          )}
          {/* Link to the specific list (if applicable and not the last item) */}
          {isListPage && isNestedAction && listTitle && (
            <Anchor component={Link} to={`/list/${parts[1]}`} className={classes.link} size='sm' truncate="end">
              {listTitle}
            </Anchor>
          )}
          {/* Display the final item */}
          {lastItemText && (
            <Text size='sm' truncate='end'>
              {lastItemText}
            </Text>
          )}
        </Base>
        <ActionButtons />
      </Group>
    </>
  );
}
