import { isRouteErrorResponse, ErrorResponse } from 'react-router';

function extractMessage(str: string): string {
  try {
    const parsed = JSON.parse(str);
    if (parsed && typeof parsed === 'object') {
      if (typeof parsed.message === 'string' && parsed.message.trim().length > 0) {
        return parsed.message;
      }
      if (typeof parsed.error === 'string' && parsed.error.trim().length > 0) {
        return parsed.error;
      }
    }
  } catch {
    // Not a JSON string
  }
  return str;
}

// TODO: add i18n support
function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    if (error.message === 'Failed to fetch')
      return `We can't access the ALA servers right now, please try again later.`;
    else if (error.message) return extractMessage(error.message);
    else return error.toString();
  } else if (isRouteErrorResponse(error)) {
    const data = (error as ErrorResponse).data;
    return typeof data === 'string' ? extractMessage(data) : 'An unknown route error occurred';
  } else if (typeof error === 'string') {
    return extractMessage(error);
  } else return 'An unknown error occurred';
}

export default getErrorMessage;
