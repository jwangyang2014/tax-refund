import React from 'react';

export default function RefundGuidancePanel(props: {
  guidance: {
    whatItMeans: string;
    actions: string[];
    quickQuestions: string[];
  };
  asking: boolean;
  onAskQuestion: (q: string) => void;
}) {
  const { guidance, asking, onAskQuestion } = props;

  return (
    <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, marginBottom: 12, maxWidth: 820 }}>
      <h4 style={{ marginTop: 0, marginBottom: 8 }}>What this status means</h4>
      <p style={{ marginTop: 0 }}>{guidance.whatItMeans}</p>

      <div style={{ marginTop: 10 }}>
        <strong>Recommended actions</strong>
        <ul style={{ marginTop: 6 }}>
          {guidance.actions.map((a, idx) => (
            <li key={idx}>{a}</li>
          ))}
        </ul>
      </div>

      <div style={{ marginTop: 10 }}>
        <strong>Quick questions</strong>
        <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {guidance.quickQuestions.map((q) => (
            <button
              key={q}
              onClick={() => onAskQuestion(q)}
              disabled={asking}
              style={{ fontSize: 13 }}
            >
              {q}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}