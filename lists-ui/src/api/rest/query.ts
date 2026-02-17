import { getCsrfToken } from '../csrf'; // Adjust path as needed

export const request = async <T>(
  input: RequestInfo | URL,
  method?: 'GET' | 'PUT' | 'POST' | 'DELETE',
  body?: BodyInit | null,
  token?: string,
  additionalHeaders?: HeadersInit
): Promise<T> => {
  
  // 1. Get the CSRF token
const csrfToken = await getCsrfToken();

// 2. Build headers using a plain object to satisfy TypeScript
const headerMap: Record<string, string> = {
  'X-XSRF-TOKEN': csrfToken,
  ...(additionalHeaders as Record<string, string> || {}),
};

// 3. Handle Authentication
if (token && token.trim() !== '') {
  headerMap['Authorization'] = `Bearer ${token}`;
}

// 4. Handle JSON Content-Type automatically
const isFormData = body instanceof FormData;
if (body && !isFormData && !headerMap['Content-Type']) {
  headerMap['Content-Type'] = 'application/json';
}

// Perform the request
const resp = await fetch(import.meta.env.VITE_API_BASEURL + input, {
  method,
  body: (body && !isFormData && typeof body === 'object') ? JSON.stringify(body) : body,
  headers: headerMap as HeadersInit, // Cast back to the expected type here
  credentials: 'include',
  signal: AbortSignal.timeout(1000 * 60 * 10),
});

  // Ensure the request was successful
  const text = await resp.text();
  if (resp.ok) {
    try {
      return JSON.parse(text) as T;
    } catch (error) {
      return text as T;
    }
  }

  throw new Error(text);
};