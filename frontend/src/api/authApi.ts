// src/api/authApi.ts
import { apiFetch, setAccessToken } from "./http";
import { readApiError } from "./error";
import type { MeResponse } from "./types";

export type RegisterPayload = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  address: string | null;
  city: string;
  state: string;
  phone: string | null;
};

export async function register(payload: RegisterPayload): Promise<void> {
  const res = await apiFetch('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  });

  if (!res.ok) throw new Error(await readApiError(res));
}

export async function login(email: string, password: string): Promise<void> {
  const res = await apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) throw new Error(await readApiError(res));

  const data = (await res.json()) as { accessToken: string };
  setAccessToken(data.accessToken);
}

export async function logout(): Promise<void> {
  await apiFetch('/api/auth/logout', { method: 'POST' });
  setAccessToken(null);
}

export async function getSession(): Promise<MeResponse> {
  const res = await apiFetch('/api/auth/session');
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as MeResponse;
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const res = await apiFetch('/api/profile/password', {
    method: 'PUT',
    body: JSON.stringify({ currentPassword, newPassword })
  });

  if (!res.ok) throw new Error(await readApiError(res));
}