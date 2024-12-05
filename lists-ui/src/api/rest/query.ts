export const request = async <T>(
  input: RequestInfo | URL,
  method?: 'GET' | 'PUT' | 'POST' | 'DELETE',
  body?: BodyInit | null,
  token?: string
): Promise<T> => {
  const headers: HeadersInit = token
    ? { Authorization: `Bearer ${token}` }
    : {};

  // Perform the request
  const resp = await fetch(import.meta.env.VITE_API_BASEURL + input, {
    method,
    body,
    headers,
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

  throw new Error(JSON.parse(text).error);
};
