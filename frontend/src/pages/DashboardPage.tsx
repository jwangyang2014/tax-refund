import React, { useCallback, useEffect, useState } from 'react';
import { getLatestRefund, simulateRefundUpdate } from '../api/refundApi';
import type { RefundStatusResponse } from '../api/.types';
import { errorMessage } from '../utils';

function nextStatus(curr: string) : string {
  const ordered = ['RECEIVED', 'PROCESSING', 'APPROVED', 'SENT', 'AVAILABLE'];
  const index = ordered.indexOf(curr);
  return index < 0 ? 'RECEIVED' : ordered[Math.min(index + 1, ordered.length - 1)];
}

export default function DashboardPage(props: {
  onLogout: () => void;
  onError: (msg: string) => void;
}) {
  const [data, setData] = useState<RefundStatusResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);

    try {
      const data = await getLatestRefund();
      setData(data);
    } catch(err: unknown) {
      props.onError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [props])

  useEffect(() => {
    load();
  }, [load]);

  async function demoAdvanceStatus() {
    if (!data) {
      return;
    }

    const next = nextStatus(data.status);

    try {
      await simulateRefundUpdate({
        taxYear: data.taxYear,
        status: next,
        expectedAmount: data.expectedAmount ?? 1234.56,
        trackingId: data.trackingId ?? 'MOCK-TRACK'
      });

      await load();
    } catch (e: unknown) {
      props.onError(errorMessage(e));
    }
  }

  return (
    <div>
      <h3>Refund Status</h3>
      <div style={{ display: 'flex', gap: 8, marginBottom: 12}}>
        <button onClick={load} disabled={loading}>
          {loading? '...' : 'Refresh'}
        </button>
        <button onClick={demoAdvanceStatus} disabled={!data}>
          Demo: Advance Status
        </button>
        <button onClick={props.onLogout}>Logout</button>
      </div>

      {!data ? (
        <p>No data yet</p>
      ) : (
        <div style={{ border: '1px solid #999', padding: 12, maxWidth: 720}}>
          <p><strong>Tax Year:</strong> {data.taxYear}</p>
          <p data-testid="refund-status"><strong>Status:</strong> {data.status}</p>
          <p><strong>Expected amount:</strong> {data.expectedAmount}</p>
          <p><strong>Tracking ID:</strong> {data.trackingId ?? 'N/A'}</p>
          <p><strong>Last updated:</strong> {new Date(data.lastUpdatedAt).toLocaleString()}</p>

          {data.availableAtEstimated ? (
            <p>
              <strong>Estimated available at:</strong>
              {new Date(data.availableAtEstimated).toLocaleString()}
            </p>
          ) : null}

          {data.aiExplanation ? (
            <p>
              <strong>AI explanation:</strong>
              {data.aiExplanation}
            </p>
          ) : null }

        </div>
      )}
    </div>
  );
}