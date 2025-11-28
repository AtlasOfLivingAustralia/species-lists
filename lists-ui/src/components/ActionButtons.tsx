import { useALA } from '#/helpers/context/useALA';
import { faCog, faFloppyDisk, faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Button, Group, Tooltip } from '@mantine/core';
import { FormattedMessage, useIntl } from 'react-intl';
import { Link, useLocation } from 'react-router';

export function ActionButtons() {
  const ala = useALA();
  const intl = useIntl();
  const location = useLocation();

  return (
    <Group gap='xs'>
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
      <Tooltip label={intl.formatMessage({ id: 'upload.button.title' })} position='left' opacity={0.8} withArrow>
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
      </Tooltip>
      {ala.isAdmin && (
        <Button
          variant='default'
          radius='xl'
          component={Link}
          to='/admin'
          leftSection={<FontAwesomeIcon icon={faCog} />}
        >
          Admin
        </Button>
      )}
    </Group>
  );
}
