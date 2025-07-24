import { userManager } from '#/main';
import { AuthContextProps } from 'react-oidc-context';
import handleSignout from './handleSignout';

interface TokenRefreshPayload {
  access_token: string;
  expires_in: number;
  id_token: string;
  token_type: string;
}

export default async function handleRefresh(auth: AuthContextProps) {
  // Ensure the user exists
  let existing = auth.user;
  if (!existing) {
    await handleSignout(auth);
    return;
  }

  // Construct the form data for the request
  const params = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: import.meta.env.VITE_AUTH_CLIENT_ID,
    refresh_token: auth.user?.refresh_token || '',
  });

  // Make the token refresh request
  const tokenEndpoint = await userManager.metadataService.getTokenEndpoint();
  const resp = await fetch(tokenEndpoint || '', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  });

  // If we couldn't get the token okay, sign out
  if (!resp.ok) {
    await handleSignout(auth);
    return;
  }

  // Extract the token payload data
  const { access_token, expires_in } =
    (await resp.json()) as TokenRefreshPayload;

  // Update the existing user
  existing.access_token = access_token;
  existing.expires_in = expires_in;
  existing.expires_at = Date.now() + expires_in * 1000;

  // Trigger the user event to propagate changes & update localStorage
  await auth.events.load(existing, true);
  await userManager.storeUser(existing);
}
