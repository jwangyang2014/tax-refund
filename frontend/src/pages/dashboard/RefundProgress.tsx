import React from 'react';
import { STATUS_ORDER } from './refundStatusMeta';

export default function RefundProgress(props: {
  status?: string;
  currentStepIdx: number;
}) {
  const { status, currentStepIdx } = props;

  return (
    <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, marginBottom: 12 }}>
      <div style={{ fontWeight: 600, marginBottom: 10 }}>Refund progress</div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {STATUS_ORDER.map((s, idx) => {
          const complete = currentStepIdx >= 0 && idx <= currentStepIdx;
          const active = status === s;
          return (
            <div
              key={s}
              style={{
                padding: '8px 10px',
                borderRadius: 999,
                border: '1px solid #ccc',
                background: active ? '#cfe2ff' : complete ? '#d1e7dd' : '#f8f9fa',
                fontWeight: active ? 700 : 500
              }}
            >
              {idx + 1}. {s}
            </div>
          );
        })}

        {status && (status === 'REJECTED' || status === 'NOT_FOUND') && (
          <div
            style={{
              padding: '8px 10px',
              borderRadius: 999,
              border: '1px solid #ccc',
              background: '#f8d7da',
              fontWeight: 700
            }}
          >
            ! {status}
          </div>
        )}
      </div>
    </div>
  );
}