import { hasAuthParams } from 'react-oidc-context';

export default function handleCallback() {
  const params = new URLSearchParams(window.location.search);

  if (hasAuthParams(window.location)) {
    params.delete('code');
    params.delete('state');

    // Remove the auth code & state variables from the history
    window.history.replaceState(
      null,
      '',
      window.location.origin + window.location.pathname + params.toString()
    );
  } else {
    console.log('[Main] onSigninCallback', 'No auth params in location!');
  }
}
