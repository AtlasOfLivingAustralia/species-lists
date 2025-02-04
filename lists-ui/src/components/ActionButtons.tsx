import { useALA } from '#/helpers/context/useALA';
import { faCog, faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Button, Group } from '@mantine/core';
import { Link } from 'react-router';

export function ActionButtons() {
  const ala = useALA();

  if (!ala.isAuthenticated) return null;

  return (
    <Group gap='xs'>
      <Button
        variant='ala-secondary'
        component={Link}
        to='/upload'
        leftSection={<FontAwesomeIcon icon={faUpload} />}
      >
        Upload List
      </Button>
      {ala.isAdmin && (
        <Button
          variant='ala-secondary'
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
