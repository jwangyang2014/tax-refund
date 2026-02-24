import React from 'react';

export default function RefundStatusBanner(props: {
  hasData: boolean;
  tone: { label: string; color: string; bg: string };
  title: string;
  freshness?: string;
  lastUpdatedText?: string;
}) {
  return (
    <div
      style={{
        border: '1px solid #ddd',
        borderRadius: 8,
        padding: 12,
        marginBottom: 12,
        background: props.tone.bg
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
        <div>
          <div style={{ fontWeight: 700, color: props.tone.color }}>{props.tone.label}</div>
          <div style={{ color: '#333', marginTop: 4 }}>
            {props.hasData ? props.title : 'Loading refund status...'}
          </div>
        </div>

        {props.hasData && props.freshness && props.lastUpdatedText ? (
          <div style={{ color: '#333', fontSize: 13 }}>
            <div><strong>{props.freshness}</strong></div>
            <div>{props.lastUpdatedText}</div>
          </div>
        ) : null}
      </div>
    </div>
  );
}