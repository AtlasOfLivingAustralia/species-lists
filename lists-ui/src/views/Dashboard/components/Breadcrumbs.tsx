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
  const { pathname } = useLocation();
  const parts = pathname.split('/').slice(1).filter(part => part !== '');
  
  // Check if we're on a list page or one of its subpages
  const isList = parts[0] === 'list' && parts.length > 1;
  
  // Create breadcrumb items array
  const items = [];
  
  // Home link (always present)
  items.push(
    <Anchor href={import.meta.env.VITE_ALA_HOME_PAGE} className={classes.link} size='sm' key="home">
      Home
    </Anchor>
  );
  
  // Species lists link (or text if we're on the main species lists page)
  if (parts.length > 0) {
    items.push(
      <Anchor component={Link} to='/' className={classes.link} size='sm' key="species-lists">
        Species lists
      </Anchor>
    );
  } else {
    items.push(<Text size='sm' key="species-lists">Species lists</Text>);
  }

  // Handle standalone pages like '/upload'
  if (parts.length === 1 && parts[0] !== 'list') {
    items.push(
      <Text size='sm' truncate='end' key="page">
        {capitalize(parts[0])}
      </Text>
    );
  }
  
  // Handle list pages and subpages
  else if (isList) {
    // Add the list title if available
    if (listTitle) {
      // If we're on a subpage of the list (like "reingest"), make the list title a link
      if (parts.length > 2) {
        items.push(
          <Anchor 
            component={Link} 
            to={`/list/${parts[1]}`} 
            className={classes.link} 
            size='sm'
            key="list-title"
          >
            {listTitle}
          </Anchor>
        );
        
        // Add any additional path segments as the final text item
        for (let i = 2; i < parts.length; i++) {
          if (i === parts.length - 1) {
            items.push(
              <Text size='sm' truncate='end' key={`part-${i}`}>
                {capitalize(parts[i])}
              </Text>
            );
          } else {
            // For any intermediate segments (unlikely but handling just in case)
            const linkPath = `/list/${parts[1]}/${parts.slice(2, i + 1).join('/')}`;
            items.push(
              <Anchor 
                component={Link} 
                to={linkPath} 
                className={classes.link} 
                size='sm'
                key={`part-${i}`}
              >
                {capitalize(parts[i])}
              </Anchor>
            );
          }
        }
      } else {
        // Just the list page itself
        items.push(
          <Text size='sm' truncate='end' key="list-title">
            {listTitle}
          </Text>
        );
      }
    } else {
      // No list title available, use path parts as fallback
      if (parts.length > 2) {
        // For URLs like /list/{id}/reingest without a title, show ID as link
        items.push(
          <Anchor 
            component={Link} 
            to={`/list/${parts[1]}`} 
            className={classes.link} 
            size='sm'
            key="list-id"
          >
            {parts[1]}
          </Anchor>
        );
        
        // Show the action (like "reingest") as text
        items.push(
          <Text size='sm' truncate='end' key="action">
            {capitalize(parts[parts.length - 1])}
          </Text>
        );
      } else {
        // Just showing the ID as text
        items.push(
          <Text size='sm' truncate='end' key="list-id">
            {parts[1]}
          </Text>
        );
      }
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
          {items}
        </Base>
        <ActionButtons />
      </Group>
    </>
  );
}