export const getAccessToken = (): string | undefined => {
  const userRaw = localStorage.getItem(
    `oidc.user:${import.meta.env.VITE_AUTH_AUTHORITY}:${
      import.meta.env.VITE_AUTH_CLIENT_ID
    }`
  );

  // Check whether a user is stored in localStorage
  if (userRaw) {
    const user = JSON.parse(userRaw);
    if (user['expires_at'] * 1000 < Date.now()) return undefined;
    if (user['access_token']) return user['access_token'];
  }

  return undefined;
};
