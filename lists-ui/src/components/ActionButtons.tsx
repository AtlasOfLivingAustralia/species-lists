import { useALA } from '#/helpers/context/useALA';
import { faChevronDown, faCog, faFloppyDisk, faList, faTools, faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Button, Group, Menu } from '@mantine/core';
import { FormattedMessage, useIntl } from 'react-intl';
import { Link, useLocation } from 'react-router';

export function ActionButtons() {
  const ala = useALA();
  const intl = useIntl();
  const location = useLocation();

  return (
    <Group gap='xs' ml="auto" justify="flex-end">
      {ala.isAuthenticated && (
        <Button
            component={Link}
            to='/my-lists'
            variant={location.pathname != '/my-lists' ? 'default' : 'filled'} // 'default'
            radius='xl'
            title={intl.formatMessage({
              id: 'myLists.label',
              defaultMessage: 'My Lists',
            })}
            aria-label={intl.formatMessage({
              id: 'myLists.label',
              defaultMessage: 'My Lists',
            })}
            leftSection={<FontAwesomeIcon icon={faFloppyDisk} />}
          >
            <FormattedMessage
              id='myLists.label'
              defaultMessage='My Lists'
            />
          </Button>
      )}
        <Button
          variant='default'
          radius='xl'
          component={Link}
          title={intl.formatMessage({ id: 'upload.button.title' })}
          aria-label={intl.formatMessage({ id: 'upload.button.title' })}
          to='/upload'
          leftSection={<FontAwesomeIcon icon={faUpload} />}
        >
          <FormattedMessage id='upload.button.label' defaultMessage='Upload List'/>
        </Button>
      {ala.isAdmin && (
        <Menu shadow="md" width={200}>
          {/* 1. Menu Target (The visible button) */}
          <Menu.Target>
            <Button
              variant='default'
              radius='xl'
              leftSection={<FontAwesomeIcon icon={faCog} />}
              rightSection={<FontAwesomeIcon icon={faChevronDown} size="xs" />}
            >
              Admin Menu
            </Button>
          </Menu.Target>

          {/* 2. Menu Dropdown (The panel that opens) */}
          <Menu.Dropdown>
            {/* First Menu Item: "View all lists" */}
            <Menu.Item
              component={Link} // Use Link for navigation
              to="/admin-lists"
              leftSection={<FontAwesomeIcon icon={faList} />}
            >
              View all lists
            </Menu.Item>

            {/* Second Menu Item: "Admin tasks" */}
            <Menu.Item
              component={Link} // Use Link for navigation
              to="/admin"
              leftSection={<FontAwesomeIcon icon={faTools} />}
            >
              Admin tasks
            </Menu.Item>
          </Menu.Dropdown>
        </Menu>
      )}
    </Group>
  );
}
