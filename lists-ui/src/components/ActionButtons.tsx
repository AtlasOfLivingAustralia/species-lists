import { useALA } from '#/helpers/context/useALA';
import { faCog, faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Button, Group } from '@mantine/core';
import { Link } from 'react-router';
import { FormattedMessage } from 'react-intl';

export function ActionButtons() {
  const ala = useALA();

  return (
    <Group gap='xs'>
      <Button
        variant='default'
        radius='xl'
        component={Link}
        to='/upload'
        leftSection={<FontAwesomeIcon icon={faUpload} />}
      >
        <FormattedMessage id='upload.button.label' defaultMessage='Upload List'/>
      </Button>
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
