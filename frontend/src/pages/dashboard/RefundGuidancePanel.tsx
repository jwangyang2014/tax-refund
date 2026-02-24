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
    <section className="dashboard-card">
      <h4 className="card-section-title">What this status means</h4>
      <p className="card-paragraph">{guidance.whatItMeans}</p>

      <div className="card-block">
        <div className="card-subtitle">Recommended actions</div>
        <ul className="clean-list">
          {guidance.actions.map((a, idx) => (
            <li key={idx}>{a}</li>
          ))}
        </ul>
      </div>

      <div className="card-block">
        <div className="card-subtitle">Quick questions</div>
        <div className="chip-wrap">
          {guidance.quickQuestions.map((q) => (
            <button
              key={q}
              onClick={() => onAskQuestion(q)}
              disabled={asking}
              className="chip-btn"
            >
              {q}
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}