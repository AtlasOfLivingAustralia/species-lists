import { useALA } from '#/helpers/context/useALA';
import { faCog, faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Button, Group, Tooltip } from '@mantine/core';
import { Link } from 'react-router';
import { FormattedMessage, useIntl } from 'react-intl';

export function ActionButtons() {
  const ala = useALA();
  const intl = useIntl();

  return (
    <Group gap='xs'>
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
