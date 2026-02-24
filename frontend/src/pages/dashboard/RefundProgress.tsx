import React from 'react';
import { STATUS_ORDER } from './refundStatusMeta';

export default function RefundProgress(props: {
  status?: string;
  currentStepIdx: number;
}) {
  const { status, currentStepIdx } = props;

  return (
    <section className="dashboard-card">
      <div className="card-title">Refund progress</div>

      <div className="progress-pills">
        {STATUS_ORDER.map((s, idx) => {
          const complete = currentStepIdx >= 0 && idx <= currentStepIdx;
          const active = status === s;

          const cls = active
            ? 'progress-pill is-active'
            : complete
            ? 'progress-pill is-complete'
            : 'progress-pill';

          return (
            <div key={s} className={cls}>
              <span className="progress-pill-index">{idx + 1}</span>
              <span>{s}</span>
            </div>
          );
        })}

        {status && (status === 'REJECTED' || status === 'NOT_FOUND') && (
          <div className="progress-pill is-alert">
            <span className="progress-pill-index">!</span>
            <span>{status}</span>
          </div>
        )}
      </div>
    </section>
  );
}