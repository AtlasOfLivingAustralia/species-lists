import { isRouteErrorResponse, ErrorResponse } from 'react-router';
// TODO: add i18n support
function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    if (error.message === 'Failed to fetch')
      return `We can't access the ALA servers right now, please try again later.`;
    else if (error.message) return error.message;
    else return error.toString();
  } else if (isRouteErrorResponse(error)) {
    return (error as ErrorResponse).data;
  } else if (typeof error === 'string') {
    return error;
  } else return 'An unknown error occurred';
}

export default getErrorMessage;
