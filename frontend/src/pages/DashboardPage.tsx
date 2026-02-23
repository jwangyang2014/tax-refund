import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { getLatestRefund, simulateRefundUpdate } from '../api/refundApi';
import { askAssistant, type AssistantResponse } from '../api/assistantApi';
import type { RefundStatusResponse } from '../api/.types';
import { errorMessage } from '../utils';

const STATUS_ORDER = ['RECEIVED', 'PROCESSING', 'APPROVED', 'SENT', 'AVAILABLE'] as const;

function nextStatus(curr: string): string {
  const index = STATUS_ORDER.indexOf(curr as (typeof STATUS_ORDER)[number]);
  if (index < 0) return STATUS_ORDER[0];
  return STATUS_ORDER[(index + 1) % STATUS_ORDER.length];
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return 'N/A';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return String(iso);
  }
}

function getStatusStepIndex(status: string): number {
  const idx = STATUS_ORDER.indexOf(status as (typeof STATUS_ORDER)[number]);
  return idx < 0 ? -1 : idx;
}

function getStatusTone(status: string): { label: string; color: string; bg: string } {
  switch (status) {
    case 'AVAILABLE':
      return { label: 'Refund available', color: '#0f5132', bg: '#d1e7dd' };
    case 'REJECTED':
      return { label: 'Needs attention', color: '#842029', bg: '#f8d7da' };
    case 'NOT_FOUND':
      return { label: 'Not found', color: '#664d03', bg: '#fff3cd' };
    case 'PROCESSING':
    case 'APPROVED':
    case 'SENT':
    case 'RECEIVED':
    default:
      return { label: 'In progress', color: '#055160', bg: '#cff4fc' };
  }
}

function getStatusGuidance(status: string): {
  title: string;
  whatItMeans: string;
  actions: string[];
  quickQuestions: string[];
} {
  switch (status) {
    case 'RECEIVED':
      return {
        title: 'We received your refund request',
        whatItMeans:
          'Your return/refund request has been received and is waiting in the processing pipeline. This is normal early in the flow.',
        actions: [
          'Check back later for the next status update',
          'Use the assistant to ask for an ETA estimate',
          'Make sure your filing details are complete in your profile'
        ],
        quickQuestions: [
          'When will my refund be available?',
          'What happens after RECEIVED?',
          'Why is my refund still in received status?'
        ]
      };
    case 'PROCESSING':
      return {
        title: 'Your refund is being processed',
        whatItMeans:
          'Your refund is under review/processing. During this stage, timing can vary due to tax-season volume and upstream processing delays.',
        actions: [
          'Check the estimated availability date',
          'Ask the assistant why processing may take longer',
          'Contact support if there is no update after 21 days'
        ],
        quickQuestions: [
          'Why is my refund delayed?',
          'When should I contact support?',
          'What is my estimated refund date?'
        ]
      };
    case 'APPROVED':
      return {
        title: 'Your refund has been approved',
        whatItMeans:
          'Approval means your refund passed processing checks. The next step is disbursement and then availability.',
        actions: [
          'Check for “SENT” and “AVAILABLE” updates',
          'Ask the assistant for estimated availability timing',
          'Keep your bank/deposit details current if applicable'
        ],
        quickQuestions: [
          'How long after approval is refund available?',
          'What does approved mean?',
          'What are the next steps?'
        ]
      };
    case 'SENT':
      return {
        title: 'Your refund has been sent',
        whatItMeans:
          'The refund has been sent to the payment/disbursement channel. Availability may still take a short time depending on the receiving institution.',
        actions: [
          'Check tracking details if available',
          'Refresh status later if not yet marked AVAILABLE',
          'Ask the assistant what “sent” means in plain language'
        ],
        quickQuestions: [
          'Why is it sent but not available yet?',
          'Show tracking details',
          'How long after sent will it be available?'
        ]
      };
    case 'AVAILABLE':
      return {
        title: 'Your refund is available',
        whatItMeans:
          'Your refund is now available. You should be able to access it through the expected payment channel.',
        actions: [
          'Confirm receipt in your payment account',
          'Save a screenshot/export for your records (future enhancement)',
          'Ask the assistant if you have questions about the timeline'
        ],
        quickQuestions: [
          'My refund says available, what should I do next?',
          'Can you summarize my refund timeline?',
          'How long did my refund take?'
        ]
      };
    case 'REJECTED':
      return {
        title: 'Your refund needs attention',
        whatItMeans:
          'The refund request was rejected or could not be completed. Usually this means additional review or a correction is needed.',
        actions: [
          'Contact support for the exact reason',
          'Review your filing details for errors',
          'Ask the assistant what steps to take next'
        ],
        quickQuestions: [
          'Why was my refund rejected?',
          'What should I do next?',
          'Contact support'
        ]
      };
    case 'NOT_FOUND':
      return {
        title: 'No refund status found',
        whatItMeans:
          'We could not find a refund record yet. This can happen if filing is recent or the upstream system has not synced.',
        actions: [
          'Refresh later',
          'Confirm your filing was submitted successfully',
          'Contact support if this persists'
        ],
        quickQuestions: [
          'Why is my refund not found?',
          'When should I check again?',
          'Contact support'
        ]
      };
    default:
      return {
        title: 'Refund status',
        whatItMeans: 'Use the assistant or refresh your refund status for the latest information.',
        actions: ['Refresh status', 'Ask a question'],
        quickQuestions: ['What is my refund status?', 'When will my refund be available?']
      };
  }
}

function getFreshnessLabel(lastUpdatedAt?: string | null): string {
  if (!lastUpdatedAt) return 'Unknown freshness';
  const diffMs = Date.now() - new Date(lastUpdatedAt).getTime();
  if (Number.isNaN(diffMs)) return 'Unknown freshness';

  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return 'Updated just now';
  if (mins < 60) return `Updated ${mins} min ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `Updated ${hrs} hr ago`;
  const days = Math.floor(hrs / 24);
  return `Updated ${days} day${days > 1 ? 's' : ''} ago`;
}

function ConfidenceBadge({ confidence }: { confidence: AssistantResponse['confidence'] }) {
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

export default function DashboardPage({
  onLogout,
  onError
}: {
  onLogout: () => void;
  onError: (msg: string) => void;
}) {
  const [data, setData] = useState<RefundStatusResponse | null>(null);
  const [loading, setLoading] = useState(false);

  // Assistant UI state
  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);
  const [assistant, setAssistant] = useState<AssistantResponse | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const latest = await getLatestRefund();
      setData(latest);
    } catch (err: unknown) {
      onError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [onError]);

  useEffect(() => {
    load();
  }, [load]);

  async function demoAdvanceStatus() {
    if (!data) return;

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
      onError(errorMessage(e));
    }
  }

  async function onAsk(q?: string) {
    const finalQuestion = (q ?? question).trim();
    if (!finalQuestion) return;

    setQuestion(finalQuestion);
    setAsking(true);
    try {
      const resp = await askAssistant(finalQuestion);
      setAssistant(resp);
    } catch (e: unknown) {
      onError(errorMessage(e));
    } finally {
      setAsking(false);
    }
  }

  function handleAction(a: AssistantResponse['actions'][number]) {
    if (a.type === 'REFRESH') {
      load();
      return;
    }

    if (a.type === 'SHOW_TRACKING') {
      const el = document.getElementById('refund-details-card');
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }

    if (a.type === 'CONTACT_SUPPORT') {
      const subject = encodeURIComponent('Refund Help');
      const body = encodeURIComponent(
        `Hi Support,\n\nI need help with my refund status.\n\nStatus: ${data?.status ?? 'Unknown'}\nTax Year: ${data?.taxYear ?? 'Unknown'}\nLast Updated: ${data?.lastUpdatedAt ?? 'Unknown'}\n\nThanks.`
      );
      window.location.href = `mailto:support@example.com?subject=${subject}&body=${body}`;
      return;
    }
  }

  const tone = getStatusTone(data?.status ?? '');
  const guidance = useMemo(() => getStatusGuidance(data?.status ?? ''), [data?.status]);
  const freshness = getFreshnessLabel(data?.lastUpdatedAt);
  const currentStepIdx = getStatusStepIndex(data?.status ?? '');

  return (
    <div style={{ maxWidth: 860 }}>
      <h3 style={{ marginBottom: 8 }}>Refund Status</h3>

      <p style={{ marginTop: 0, color: '#555', maxWidth: 760 }}>
        Check your latest refund status, see the estimated availability date, and get guided help if something looks delayed.
      </p>

      <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
        <button onClick={load} disabled={loading}>
          {loading ? '...' : 'Refresh'}
        </button>

        <button onClick={demoAdvanceStatus} disabled={!data}>
          Demo: Advance Status
        </button>

        <button onClick={() => onAsk('When will my refund be available?')} disabled={!data || asking}>
          Quick: Ask ETA
        </button>

        <button onClick={onLogout}>Logout</button>
      </div>

      {/* Status Summary / Banner */}
      <div
        style={{
          border: '1px solid #ddd',
          borderRadius: 8,
          padding: 12,
          marginBottom: 12,
          background: tone.bg
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
          <div>
            <div style={{ fontWeight: 700, color: tone.color }}>{tone.label}</div>
            <div style={{ color: '#333', marginTop: 4 }}>
              {data ? `${guidance.title}` : 'Loading refund status...'}
            </div>
          </div>
          {data && (
            <div style={{ color: '#333', fontSize: 13 }}>
              <div><strong>{freshness}</strong></div>
              <div>{formatDateTime(data.lastUpdatedAt)}</div>
            </div>
          )}
        </div>
      </div>

      {/* Progress Timeline */}
      <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, marginBottom: 12 }}>
        <div style={{ fontWeight: 600, marginBottom: 10 }}>Refund progress</div>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {STATUS_ORDER.map((s, idx) => {
            const complete = currentStepIdx >= 0 && idx <= currentStepIdx;
            const active = data?.status === s;
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
          {data && (data.status === 'REJECTED' || data.status === 'NOT_FOUND') && (
            <div
              style={{
                padding: '8px 10px',
                borderRadius: 999,
                border: '1px solid #ccc',
                background: '#f8d7da',
                fontWeight: 700
              }}
            >
              ! {data.status}
            </div>
          )}
        </div>
      </div>

      {/* Core refund details */}
      {!data ? (
        <p>No data yet</p>
      ) : (
        <div
          id="refund-details-card"
          style={{ border: '1px solid #999', borderRadius: 8, padding: 12, maxWidth: 820, marginBottom: 12 }}
        >
          <p><strong>Tax Year:</strong> {data.taxYear}</p>
          <p data-testid="refund-status"><strong>Status:</strong> {data.status}</p>
          <p><strong>Expected amount:</strong> {data.expectedAmount ?? 'N/A'}</p>
          <p><strong>Tracking ID:</strong> {data.trackingId ?? 'N/A'}</p>
          <p><strong>Last updated:</strong> {formatDateTime(data.lastUpdatedAt)}</p>

          {data.availableAtEstimated && (
            <p>
              <strong>Estimated available at:</strong> {formatDateTime(data.availableAtEstimated)}
            </p>
          )}

          {!data.availableAtEstimated && data.status !== 'AVAILABLE' && data.status !== 'REJECTED' && (
            <p style={{ color: '#555' }}>
              ETA not available yet. Try refreshing later or ask the assistant for help.
            </p>
          )}
        </div>
      )}

      {/* Guidance panel */}
      {data && (
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
                  onClick={() => onAsk(q)}
                  disabled={asking}
                  style={{ fontSize: 13 }}
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Assistant section */}
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
              if (e.key === 'Enter') void onAsk();
            }}
          />
          <button onClick={() => onAsk()} disabled={asking || !question.trim()}>
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

              {/* Demo-simple markdown rendering: show as plain text */}
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontFamily: 'inherit' }}>
                {assistant.answerMarkdown}
              </pre>
            </div>

            {assistant.actions?.length > 0 && (
              <div style={{ marginTop: 10 }}>
                <strong>Suggested actions</strong>
                <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  {assistant.actions.map((a, idx) => (
                    <button key={`${a.type}-${idx}`} onClick={() => handleAction(a)}>
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
    </div>
  );
}