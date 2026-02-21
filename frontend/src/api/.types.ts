export type RefundStatusResponse = {
  taxYear: number;
  status: string;
  lastUpdatedAt: string;
  expectedAmount: number | null;
  trackingId: string | null;
  availableAtEstimated: string | null;
  aiExplanation: string | null;
}