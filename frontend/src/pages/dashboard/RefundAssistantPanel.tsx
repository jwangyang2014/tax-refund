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
    <div style={{ marginTop: 18, border: '1px solid #999', borderRadius: 8, padding: 12, maxWidth: 820 }}>
      <h4 style={{ marginTop: 0 }}>Ask about your refund</h4>

      <p style={{ marginTop: 0, color: '#555' }}>
        Ask in plain English, for example: “Why is my refund delayed?” or “What should I do next?”
      </p>

      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
        <input
          aria-label="Assistant question"
          style={{ flex: 1, padding: 8 }}
          placeholder="E.g., When will my refund be available? Why is it stuck in processing?"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') onAsk();
          }}
        />
        <button onClick={onAsk} disabled={asking || !question.trim()}>
          {asking ? '...' : 'Ask'}
        </button>
      </div>

      {!assistant ? (
        <p style={{ margin: 0 }}>No assistant response yet.</p>
      ) : (
        <div>
          <div style={{ padding: 10, border: '1px solid #ddd', borderRadius: 6 }}>
            <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
              <strong>Assistant response</strong>
              <ConfidenceBadge confidence={assistant.confidence} />
            </div>

            <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontFamily: 'inherit' }}>
              {assistant.answerMarkdown}
            </pre>
          </div>

          {assistant.actions?.length > 0 && (
            <div style={{ marginTop: 10 }}>
              <strong>Suggested actions</strong>
              <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {assistant.actions.map((a, idx) => (
                  <button key={`${a.type}-${idx}`} onClick={() => onAction(a)}>
                    {a.label}
                  </button>
                ))}
              </div>
            </div>
          )}

          {assistant.citations?.length > 0 && (
            <div style={{ marginTop: 10 }}>
              <strong>Citations</strong>
              <ul>
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
    </div>
  );
}