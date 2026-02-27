import { notifyAuthExpired } from "../authEvents";

let accessToken: string | null = null;
const SERVER_URL = import.meta.env.VITE_SERVER_URL ?? '';

export function setAccessToken(token: string | null) {
  accessToken = token;
}

async function refreshAccessToken(): Promise<string> {
  const res = await fetch(`${SERVER_URL}/api/auth/refresh`, {
    method: 'POST',
    credentials: 'include'
  });

  if (!res.ok) {
    throw new Error('Refresh access token failed');
  }

  const data = (await res.json()) as { accessToken: string };
  setAccessToken(data.accessToken);

  return data.accessToken;
}

let refreshInFlight: Promise<string> | null = null;

async function refreshAccessTokenShared(): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = refreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export async function apiFetch(url: string, init: RequestInit = {}): Promise<Response> {
  let res = await doFetch(url, init);

  // If unauthorized, attempt to refresh once
  if (res.status === 401) {
    try {
      await refreshAccessTokenShared();
      res = await doFetch(url, init);
    } catch {
      // refresh failed
      setAccessToken(null);
      notifyAuthExpired();
      return res;
    }

    // if still unauthorized after refresh, session is no longer valid
    if (res.status === 401) {
      setAccessToken(null);
      notifyAuthExpired();
    }
  }

  return res;
}

async function doFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers || {});

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  return await fetch(`${SERVER_URL}${url}`, {
    ...init,
    headers,
    credentials: 'include'
  });
}