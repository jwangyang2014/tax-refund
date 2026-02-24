import React from 'react';

export default function RefundStatusBanner(props: {
  hasData: boolean;
  tone: { label: string; color: string; bg: string };
  title: string;
  freshness?: string;
  lastUpdatedText?: string;
}) {
  const toneClass =
    props.tone.label === 'Refund available'
      ? 'status-banner tone-success'
      : props.tone.label === 'Needs attention'
      ? 'status-banner tone-danger'
      : props.tone.label === 'Not found'
      ? 'status-banner tone-warning'
      : 'status-banner tone-info';

  return (
    <section className={toneClass} aria-label="Refund summary">
      <div className="status-banner-content">
        <div>
          <div className="status-banner-label">{props.tone.label}</div>
          <div className="status-banner-title">
            {props.hasData ? props.title : 'Loading refund status...'}
          </div>
        </div>

        {props.hasData && props.freshness && props.lastUpdatedText ? (
          <div className="status-banner-meta">
            <div className="status-banner-freshness">{props.freshness}</div>
            <div>{props.lastUpdatedText}</div>
          </div>
        ) : null}
      </div>
    </section>
  );
}