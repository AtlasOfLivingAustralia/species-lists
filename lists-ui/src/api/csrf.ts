export async function getCsrfToken(): Promise<string> {  
  const getCookie = () => {
    const cookies = document.cookie.split(';');  
    for (const cookie of cookies) {  
      const [name, value] = cookie.trim().split('=');  
      if (name === 'XSRF-TOKEN') {  
        return decodeURIComponent(value);  
      }  
    }
    return null;
  };

  let token = getCookie();
    
  // If no cookie found, hit the backend once to trigger the 'Set-Cookie' header
  if (!token) {  
    await fetch(import.meta.env.VITE_API_BASEURL + '/csrf', {  
      method: 'GET',  
      credentials: 'include',  
    });  
    // Now that the request finished, the browser should have the cookie
    token = getCookie();
  }  
    
  return token || '';  
}