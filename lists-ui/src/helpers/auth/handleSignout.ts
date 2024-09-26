import { AuthContextProps } from 'react-oidc-context';

export default async function handleSignout(auth: AuthContextProps) {
  const params = new URLSearchParams({
    client_id: import.meta.env.VITE_AUTH_CLIENT_ID,
    redirect_uri: import.meta.env.VITE_AUTH_REDIRECT_URI,
    logout_uri: import.meta.env.VITE_AUTH_REDIRECT_URI,
  });

  await auth.removeUser();
  window.location.replace(
    `${import.meta.env.VITE_AUTH_END_SESSION_URI}?${params.toString()}`
  );
}
