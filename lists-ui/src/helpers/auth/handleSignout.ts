import { AuthContextProps } from 'react-oidc-context';
import { getConfig } from '#/config';

export default async function handleSignout(auth: AuthContextProps) {
  if (getConfig('VITE_AUTH_AUTHORITY').startsWith('https://cognito-idp')) {
    const params = new URLSearchParams({
      client_id: getConfig('VITE_AUTH_CLIENT_ID'),
      redirect_uri: getConfig('VITE_AUTH_REDIRECT_URI'),
      logout_uri: getConfig('VITE_AUTH_REDIRECT_URI'),
      response_type: 'code',
    });

    await auth.removeUser();
    window.location.replace(
      `${getConfig('VITE_AUTH_END_SESSION_URI')}?${params.toString()}`
    );
  } else {
    await auth.signoutRedirect({
      post_logout_redirect_uri: getConfig('VITE_AUTH_REDIRECT_URI'),
    });
  }
}
