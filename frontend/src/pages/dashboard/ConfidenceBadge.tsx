import React from 'react';
import type { AssistantResponse } from '../../api/assistantApi';

export default function ConfidenceBadge({
  confidence
}: {
  confidence: AssistantResponse['confidence'];
}) {
  const style =
    confidence === 'HIGH'
      ? { bg: '#d1e7dd', color: '#0f5132' }
      : confidence === 'MEDIUM'
      ? { bg: '#fff3cd', color: '#664d03' }
      : { bg: '#f8d7da', color: '#842029' };

  return (
    <span
      style={{
        display: 'inline-block',
        padding: '2px 8px',
        borderRadius: 999,
        background: style.bg,
        color: style.color,
        fontSize: 12,
        fontWeight: 600
      }}
    >
      {confidence}
    </span>
  );
}