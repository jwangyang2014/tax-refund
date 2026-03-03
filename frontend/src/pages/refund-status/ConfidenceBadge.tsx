import React from 'react';
import type { AssistantResponse } from '../../api/assistantApi';

export default function ConfidenceBadge({
  confidence
}: {
  confidence: AssistantResponse['confidence'];
}) {
  const cls =
    confidence === 'HIGH'
      ? 'confidence-badge confidence-high'
      : confidence === 'MEDIUM'
      ? 'confidence-badge confidence-medium'
      : 'confidence-badge confidence-low';

  return <span className={cls}>{confidence}</span>;
}