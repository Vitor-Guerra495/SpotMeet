import type { SessionSnapshot } from '../context/AuthContext';

function isProtectedApiRequest(input: RequestInfo | URL, apiBaseUrl: string): boolean {
  try {
    const url = new URL(typeof input === 'string' ? input : 'url' in input ? input.url : input.toString());
    const base = new URL(apiBaseUrl);
    const prefix = base.pathname.replace(/\/$/, '');
    return url.origin === base.origin && url.pathname.startsWith(`${prefix}/`)
      && url.pathname !== `${prefix}/auth` && !url.pathname.startsWith(`${prefix}/auth/`);
  } catch {
    return false;
  }
}

export function installSessionFetch(
  getApiBaseUrl: () => string,
  getSession: () => SessionSnapshot,
  onExpired: (session: SessionSnapshot) => void,
): () => void {
  const originalFetch = globalThis.fetch;
  let active = true;
  const sessionFetch: typeof fetch = async (input, init) => {
    const apiBaseUrl = getApiBaseUrl();
    const session = getSession();
    const requestHeaders = typeof input === 'object' && 'headers' in input ? input.headers : undefined;
    const signal = init?.signal ?? (typeof input === 'object' && 'signal' in input ? input.signal : undefined);
    const headers = new Headers(init?.headers ?? requestHeaders);
    const guarded = session.token !== null && isProtectedApiRequest(input, apiBaseUrl)
      && headers.get('Authorization') === `Bearer ${session.token}`;
    const request = typeof input === 'object' && !('url' in input) ? input.toString() : input;
    const response = await originalFetch.call(globalThis, request, init);
    if (active && guarded && !signal?.aborted && response.status === 401 && apiBaseUrl === getApiBaseUrl()) {
      const current = getSession();
      if (current.token === session.token && current.version === session.version) onExpired(session);
    }
    return response;
  };
  globalThis.fetch = sessionFetch;
  return () => {
    active = false;
    if (globalThis.fetch === sessionFetch) globalThis.fetch = originalFetch;
  };
}
