import React from 'react';
import type { RefundStatusResponse } from '../../api/types';
import { formatDateTime } from './refundStatusMeta';

export default function RefundDetailsCard({ data }: { data: RefundStatusResponse | null }) {
  if (!data) return <p>No data yet</p>;

  return (
    <div
      id="refund-details-card"
      style={{ border: '1px solid #999', borderRadius: 8, padding: 12, maxWidth: 820, marginBottom: 12 }}
    >
      <p><strong>Tax Year:</strong> {data.taxYear}</p>
      <p data-testid="refund-status"><strong>Status:</strong> {data.status}</p>
      <p><strong>Expected amount:</strong> {data.expectedAmount ?? 'N/A'}</p>
      <p><strong>Tracking ID:</strong> {data.trackingId ?? 'N/A'}</p>
      <p><strong>Last updated:</strong> {formatDateTime(data.lastUpdatedAt)}</p>

      {data.availableAtEstimated && (
        <p>
          <strong>Estimated available at:</strong> {formatDateTime(data.availableAtEstimated)}
        </p>
      )}

      {!data.availableAtEstimated && data.status !== 'AVAILABLE' && data.status !== 'REJECTED' && (
        <p style={{ color: '#555' }}>
          ETA not available yet. Try refreshing later or ask the assistant for help.
        </p>
      )}
    </div>
  );
}