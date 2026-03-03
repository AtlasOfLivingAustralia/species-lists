import { getErrorMessage, ListError } from '#/helpers';
import { StopIcon } from '@atlasoflivingaustralia/ala-mantine';
import { faFile } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { isRouteErrorResponse, useLocation, useNavigate, useRouteError } from 'react-router';
import { Breadcrumbs } from '../views/Dashboard/components/Breadcrumbs';
import { Container, Grid } from '@mantine/core';
import { Message } from './Message';

export default function PageError() {
  const navigate = useNavigate();
  const error = useRouteError();
  const location = useLocation();

  if (error instanceof ListError) {
    return (
      <>
        <Container fluid>
          <Grid>
            <Grid.Col span={12}>
              <Breadcrumbs listTitle={error.breadcrumb} />
            </Grid.Col>
          </Grid>
        </Container>
        <Message
          title={error.title}
          subtitle={error.message}
          icon={<FontAwesomeIcon icon={faFile} fontSize={18} />}
          action='Go home'
          onAction={() => navigate('/')}
        />
      </>
    );
  }

  return isRouteErrorResponse(error) && error.status === 404 ? (
    <Message
      title='Not found'
      subtitle={`The requested page '${location.pathname}' can't be found`}
      icon={<FontAwesomeIcon icon={faFile} fontSize={18} />}
      action='Go home'
      onAction={() => navigate('/')}
    />
  ) : (
    <Message
      title='An error occurred'
      subtitle={getErrorMessage(error as Error)}
      icon={<StopIcon size={18} />}
      action='Reload page'
      onAction={() => window.location.reload()}
      withAlaContact
    />
  );
}