import {
  Anchor,
  Breadcrumbs as Base,
  Text,
  Group,
} from '@mantine/core';
import { Link, useLocation } from 'react-router'; 
import { ChevronRightIcon } from '@atlasoflivingaustralia/ala-mantine';

import classes from './Breadcrumbs.module.css';
import { ActionButtons } from '#/components/ActionButtons';

// Helper function to capitalize the first letter of a string
const capitalize = (input?: string) =>
  input ? input.charAt(0).toUpperCase() + input.slice(1) : '';

interface BreadcrumbsProps {
  listTitle: string | undefined;
}

export function Breadcrumbs({ listTitle }: BreadcrumbsProps) {
  const { pathname } = useLocation();

  // Split pathname into parts, remove empty strings, and remove the leading empty string from the initial '/'
  const pathParts = pathname.split('/').filter(part => part !== '');

  // Define the structure for breadcrumb items
  interface BreadcrumbItem {
    label: string;
    href?: string; // Use href for external links or simple anchors
    to?: string; // Use to for react-router Link
    isText?: boolean; // Flag to indicate if it should be a Text component
  }

  const items: BreadcrumbItem[] = [];

  // Home link (always present)
  items.push({
    label: 'Home',
    href: import.meta.env.VITE_ALA_HOME_PAGE, // Assuming this is an external link
  });

  // Species lists link (or text if on the species lists index)
  items.push({
    label: 'Species lists',
    to: '/',
    isText: pathParts.length === 0 || (pathParts[0] === 'list' && pathParts.length === 1), // Text if on the species lists index or '/list'
  });

  // Handle other path parts dynamically
  // Build up the path incrementally for linking
  let currentPath = '';
  pathParts.forEach((part, index) => {
    currentPath += `/${part}`;

    // Skip the first part if it's 'list' as it's handled by the 'Species lists' item
    if (index === 0 && part === 'list') {
      // If it's just '/list', the 'Species lists' item is text, so no further breadcrumb needed for '/list' itself.
      // If it's a list details page or subpage, the list title/ID will be added next.
      return;
    }

    const isLast = index === pathParts.length - 1;

    // Handle list title specifically when on a list details page or subpage
    if (pathParts[0] === 'list' && index === 1) { // This is the list ID part
      if (listTitle) {
        items.push({
          label: listTitle,
          to: isLast ? undefined : currentPath, // Link unless it's the last item
          isText: isLast, // Text if it's the last item (the list details page itself)
        });
      } else {
        // Fallback to using the list ID if no title is provided
        items.push({
          label: part,
          to: isLast ? undefined : currentPath, // Link unless it's the last item
          isText: isLast, // Text if it's the last item
        });
      }
    } else {
      // Handle all other parts
      items.push({
        label: capitalize(part),
        to: isLast ? undefined : currentPath, // Link unless it's the last item
        isText: isLast, // Text if it's the last item
      });
    }
  });


  // Render the breadcrumb items
  const breadcrumbElements = items.map((item, index) => {
    // Skip rendering the 'Species lists' link if we are on the root '/'
    if (item.to === '/' && pathParts.length === 0) {
      return <Text size='sm' key={index}>Species lists</Text>;
    }

    // If the first path part is 'list' and this item is the 'Species lists' link and there are more parts,
    // render it as a link. Otherwise, if it's the '/list' page itself (pathParts.length === 1 && pathParts[0] === 'list'), render as text.
    if (item.to === '/' && pathParts[0] === 'list' && pathParts.length > 1) {
      return (
        <Anchor component={Link} to={item.to} className={classes.link} size='sm' key={index}>
          {item.label}
        </Anchor>
      );
    }
    if (item.isText) {
      return (
        <Text size='sm' truncate='end' key={index}>
          {item.label}
        </Text>
      );
    } else if (item.href) {
      return (
        <Anchor href={item.href} className={classes.link} size='sm' key={index}>
          {item.label}
        </Anchor>
      );
    } else if (item.to) {
      // Prevent linking the 'Species lists' item if it's the last/current item displayed
      if (item.to === '/' && pathParts.length === 0) {
        return (
          <Text size='sm' key={index}>
            {item.label}
          </Text>
        )
      }
      return (
        <Anchor component={Link} to={item.to} className={classes.link} size='sm' key={index}>
          {item.label}
        </Anchor>
      );
    }
    return null; // Should not happen
  });


  return (
    <>
      <Group justify='space-between'>
        <Base
          className={classes.breadcrumbs}
          separator={<ChevronRightIcon size={12} />}
          separatorMargin={5}
        >
          {breadcrumbElements}
        </Base>
        <ActionButtons />
      </Group>
    </>
  );
}