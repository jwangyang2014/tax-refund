// src/api/types.ts
export type RefundStatusResponse = {
  taxYear: number;
  status: string;
  lastUpdatedAt: string;
  expectedAmount: number | null;
  trackingId: string | null;
  availableAtEstimated: string | null;
  aiExplanation: string | null;
};

export type MeResponse = {
  userId: number;
  email: string;
  role: string;
};

export type UserProfile = {
  userId: number;
  email: string;
  role: string;
  firstName: string;
  lastName: string;
  address: string | null;
  city: string;
  state: string;
  phone: string | null;
};

export type UpdateProfilePayload = {
  firstName: string;
  lastName: string;
  address: string | null;
  city: string;
  state: string;
  phone: string | null;
};