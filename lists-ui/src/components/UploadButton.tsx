import { useALA } from '#/helpers/context/useALA';
import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Button } from '@mantine/core';
import { Link } from 'react-router-dom';

export function UploadButton() {
  const ala = useALA();

  if (!ala.isAuthenticated) return null;

  return (
    <Button
      variant='ala-secondary'
      component={Link}
      to='/upload'
      leftSection={<FontAwesomeIcon icon={faUpload} />}
    >
      Upload List
    </Button>
  );
}
