import React from 'react';
import type { AssistantResponse } from '../../api/assistantApi';
import ConfidenceBadge from './ConfidenceBadge';

export default function RefundAssistantPanel(props: {
  question: string;
  asking: boolean;
  assistant: AssistantResponse | null;
  setQuestion: (v: string) => void;
  onAsk: () => void;
  onAction: (a: AssistantResponse['actions'][number]) => void;
}) {
  const { question, asking, assistant, setQuestion, onAsk, onAction } = props;

  return (
    <section className="refund-status-card assistant-card">
      <h4 className="card-section-title">Ask about your refund</h4>

      <p className="card-paragraph muted">
        Ask in plain English, for example: “Why is my refund delayed?” or “What should I do next?”
      </p>

      <div className="assistant-input-row">
        <input
          aria-label="Assistant question"
          className="input"
          placeholder="E.g., When will my refund be available? Why is it stuck in processing?"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') onAsk();
          }}
        />
        <button
          className="btn btn-primary btn-fixed-sm"
          onClick={onAsk}
          disabled={asking || !question.trim()}
        >
          {asking ? 'Asking...' : 'Ask'}
        </button>
      </div>

      {asking ? (
        <div className="assistant-loading">
          <div className="spinner" aria-hidden="true" />
          <div>
            <div className="assistant-loading-title">Thinking...</div>
            <div className="assistant-loading-subtitle">
              Analyzing your refund status and next steps
            </div>
          </div>
        </div>
      ) : !assistant ? (
        <div className="assistant-empty">No assistant response yet.</div>
      ) : (
        <div className="assistant-response-wrap">
          <div className="assistant-response-box">
            <div className="assistant-response-header">
              <strong>Assistant response</strong>
              <ConfidenceBadge confidence={assistant.confidence} />
            </div>

            <pre className="assistant-response-text">{assistant.answerMarkdown}</pre>
          </div>

          {assistant.actions?.length > 0 && (
            <div className="card-block">
              <div className="card-subtitle">Suggested actions</div>
              <div className="chip-wrap">
                {assistant.actions.map((a, idx) => (
                  <button
                    key={`${a.type}-${idx}`}
                    onClick={() => onAction(a)}
                    className="text-action"
                    type="button"
                  >
                    {a.label}
                  </button>
                ))}
              </div>
            </div>
          )}

          {assistant.citations?.length > 0 && (
            <div className="card-block">
              <div className="card-subtitle">Citations</div>
              <ul className="citation-list">
                {assistant.citations.map((c, idx) => (
                  <li key={idx}>
                    <code>{c.docId}</code>: {c.quote}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  );
}