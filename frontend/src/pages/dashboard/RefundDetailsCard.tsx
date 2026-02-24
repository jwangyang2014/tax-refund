import React from 'react';
import type { RefundStatusResponse } from '../../api/types';
import { formatDateTime } from './refundStatusMeta';

export default function RefundDetailsCard({ data }: { data: RefundStatusResponse | null }) {
  if (!data) {
    return <div className="dashboard-card">No data yet</div>;
  }

  return (
    <section id="refund-details-card" className="dashboard-card">
      <div className="card-title">Refund details</div>

      <div className="details-grid">
        <div className="detail-row">
          <span className="detail-label">Tax Year</span>
          <span className="detail-value">{data.taxYear}</span>
        </div>

        <div className="detail-row" data-testid="refund-status">
          <span className="detail-label">Status</span>
          <span className="detail-value">{data.status}</span>
        </div>

        <div className="detail-row">
          <span className="detail-label">Expected amount</span>
          <span className="detail-value">{data.expectedAmount ?? 'N/A'}</span>
        </div>

        <div className="detail-row">
          <span className="detail-label">Tracking ID</span>
          <span className="detail-value">{data.trackingId ?? 'N/A'}</span>
        </div>

        <div className="detail-row">
          <span className="detail-label">Last updated</span>
          <span className="detail-value">{formatDateTime(data.lastUpdatedAt)}</span>
        </div>

        {data.availableAtEstimated && (
          <div className="detail-row">
            <span className="detail-label">Estimated available at</span>
            <span className="detail-value">{formatDateTime(data.availableAtEstimated)}</span>
          </div>
        )}
      </div>

      {!data.availableAtEstimated && data.status !== 'AVAILABLE' && data.status !== 'REJECTED' && (
        <div className="inline-note">
          ETA not available yet. Try refreshing later or ask the assistant for help.
        </div>
      )}
    </section>
  );
}