import { apiFetch } from "./http";
import { RefundStatusResponse } from "./types";

export async function getLatestRefund(): Promise<RefundStatusResponse> {
  const res = await apiFetch('/api/refund/latest');

  if (!res.ok) throw new Error(await res.text());

  return (await res.json()) as RefundStatusResponse;
}

export async function simulateRefundUpdate(payload: {
  taxYear: number;
  status: string;
  expectedAmount: number;
  trackingId: string;
}): Promise<void> {
  const res = await apiFetch('/api/refund/simulate', {
    method: 'POST',
    body: JSON.stringify(payload),
  });

  if (!res.ok) throw new Error(await res.text());
}