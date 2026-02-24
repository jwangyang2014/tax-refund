// src/api/profileApi.ts
import { apiFetch } from "./http";
import { readApiError } from "./error";
import type { UserProfile, UpdateProfilePayload } from "./types";

export async function getProfile(): Promise<UserProfile> {
  const res = await apiFetch("/api/profile");
  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as UserProfile;
}

export async function updateProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
  const res = await apiFetch("/api/profile", {
    method: "PUT",
    body: JSON.stringify(payload),
  });

  if (!res.ok) throw new Error(await readApiError(res));
  return (await res.json()) as UserProfile;
}