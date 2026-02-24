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
  onError: (msg: string) => void;
}) {
  const [data, setData] = useState<RefundStatusResponse | null>(null);
  const [loading, setLoading] = useState(false);

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
      void load();
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

        <button onClick={() => void onAsk('When will my refund be available?')} disabled={!data || asking}>
          Quick: Ask ETA
        </button>
      </div>

      <RefundStatusBanner
        hasData={!!data}
        tone={tone}
        title={guidance.title}
        freshness={freshness}
        lastUpdatedText={data ? formatDateTime(data.lastUpdatedAt) : undefined}
      />

      <RefundProgress status={data?.status} currentStepIdx={currentStepIdx} />

      <RefundDetailsCard data={data} />

      {data && (
        <RefundGuidancePanel
          guidance={guidance}
          asking={asking}
          onAskQuestion={(q) => void onAsk(q)}
        />
      )}

      <RefundAssistantPanel
        question={question}
        asking={asking}
        assistant={assistant}
        setQuestion={setQuestion}
        onAsk={() => void onAsk()}
        onAction={handleAction}
      />
    </div>
  );
}