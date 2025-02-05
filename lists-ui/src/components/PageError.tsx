import { Message } from './Message';
import { getErrorMessage } from '#/helpers';
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';
import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faFile } from '@fortawesome/free-solid-svg-icons';

export default function PageError() {
  const navigate = useNavigate();
  const error = useRouteError();

  return isRouteErrorResponse(error) && error.status === 404 ? (
    <Message
      title='Not found'
      subtitle="The requested page can't be found"
      icon={<FontAwesomeIcon icon={faFile} fontSize={18} />}
      action='Go home'
      onAction={() => navigate('/')}
    />
  ) : (
    <Message
      title='An error occured'
      subtitle={getErrorMessage(error as Error)}
      icon={<StopIcon size={18} />}
      action='Reload page'
      onAction={() => window.location.reload()}
      withAlaContact
    />
  );
}
