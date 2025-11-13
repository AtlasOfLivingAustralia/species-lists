import { AuthContextProps } from 'react-oidc-context';

export default async function handleCallback(auth: AuthContextProps) {
  await auth.signoutRedirect({  
    post_logout_redirect_uri: import.meta.env.VITE_AUTH_REDIRECT_URI  
  });  
}
