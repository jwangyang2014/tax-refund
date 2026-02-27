import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { getLatestRefund, simulateRefundUpdate } from '../api/refundApi';
import { askAssistant, type AssistantResponse } from '../api/assistantApi';
import type { RefundStatusResponse } from '../api/types';
import { errorMessage } from '../utils';

import RefundStatusBanner from './dashboard/RefundStatusBanner';
import RefundProgress from './dashboard/RefundProgress';
import RefundDetailsCard from './dashboard/RefundDetailsCard';
import RefundGuidancePanel from './dashboard/RefundGuidancePanel';
import RefundAssistantPanel from './dashboard/RefundAssistantPanel';
import {
  nextStatus,
  formatDateTime,
  getStatusStepIndex,
  getStatusTone,
  getStatusGuidance,
  getFreshnessLabel
} from './dashboard/refundStatusMeta';

export default function DashboardPage({
  onError
}: {
  onError: (msg: string | null) => void;
}) {
  const [data, setData] = useState<RefundStatusResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);
  const [assistant, setAssistant] = useState<AssistantResponse | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    onError(null);

    try {
      const latest = await getLatestRefund();
      setData(latest);
      onError(null);
    } catch (err: unknown) {
      onError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [onError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function demoAdvanceStatus() {
    if (!data) return;

    const next = nextStatus(data.status);
    onError(null);

    try {
      await simulateRefundUpdate({
        taxYear: data.taxYear,
        status: next,
        expectedAmount: data.expectedAmount ?? 1234.56,
        trackingId: data.trackingId ?? 'MOCK-TRACK'
      });

      await load();
      onError(null);
    } catch (e: unknown) {
      onError(errorMessage(e));
    }
  }

  async function onAsk(q?: string) {
    const finalQuestion = (q ?? question).trim();
    if (!finalQuestion) return;

    setQuestion(finalQuestion);
    setAssistant(null);
    setAsking(true);
    onError(null);

    try {
      const resp = await askAssistant(finalQuestion);
      setAssistant(resp);
      onError(null);
    } catch (e: unknown) {
      onError(errorMessage(e));
    } finally {
      setAsking(false);
    }
  }

  function handleAction(a: AssistantResponse['actions'][number]) {
    if (a.type === 'REFRESH') {
      onError(null);
      void load();
      return;
    }

    if (a.type === 'SHOW_TRACKING') {
      onError(null);
      const el = document.getElementById('refund-details-card');
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }

    if (a.type === 'CONTACT_SUPPORT') {
      onError(null);
      const subject = encodeURIComponent('Refund Help');
      const body = encodeURIComponent(
        `Hi Support,\n\nI need help with my refund status.\n\nStatus: ${data?.status ?? 'Unknown'}\nTax Year: ${data?.taxYear ?? 'Unknown'}\nLast Updated: ${data?.lastUpdatedAt ?? 'Unknown'}\n\nThanks.`
      );
      window.location.href = `mailto:support@example.com?subject=${subject}&body=${body}`;
      return;
    }

    onError('This suggested action is not wired yet in this demo.');
  }

  const tone = getStatusTone(data?.status ?? '');
  const guidance = useMemo(() => getStatusGuidance(data?.status ?? ''), [data?.status]);
  const freshness = getFreshnessLabel(data?.lastUpdatedAt);
  const currentStepIdx = getStatusStepIndex(data?.status ?? '');

  return (
    <div className="dashboard-page">
      <div className="dashboard-hero">
        <div>
          <h3 className="dashboard-title">Refund Status</h3>
          <p className="dashboard-subtitle">
            Check your latest refund status, estimated availability date, and get guided help if something looks delayed.
          </p>
        </div>

        <div className="dashboard-actions">
          <button
            className="btn btn-secondary btn-fixed-md"
            onClick={load}
            disabled={loading}
          >
            {loading ? 'Refreshing...' : 'Refresh'}
          </button>

          <button
            className="btn btn-secondary btn-fixed-lg"
            onClick={demoAdvanceStatus}
            disabled={!data || loading}
          >
            Demo: Advance Status
          </button>

          <button
            className="btn btn-primary btn-fixed-md"
            onClick={() => void onAsk('When will my refund be available?')}
            disabled={!data || asking}
          >
            {asking ? 'Thinking...' : 'Quick: Ask ETA'}
          </button>
        </div>
      </div>

      <RefundStatusBanner
        hasData={!!data}
        tone={tone}
        title={guidance.title}
        freshness={freshness}
        lastUpdatedText={data ? formatDateTime(data.lastUpdatedAt) : undefined}
      />

      <RefundProgress status={data?.status} currentStepIdx={currentStepIdx} />

      <div className="dashboard-grid">
        <div className="dashboard-main">
          <RefundDetailsCard data={data} />
          {data && (
            <RefundGuidancePanel
              guidance={guidance}
              asking={asking}
              onAskQuestion={(qq) => void onAsk(qq)}
            />
          )}
        </div>

        <div className="dashboard-side">
          <RefundAssistantPanel
            question={question}
            asking={asking}
            assistant={assistant}
            setQuestion={setQuestion}
            onAsk={() => void onAsk()}
            onAction={handleAction}
          />
        </div>
      </div>
    </div>
  );
}