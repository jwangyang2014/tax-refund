import React from 'react';
import { getPasswordChecklist } from '../utils/passwordRules';

export default function PasswordRequirements({ password }: { password: string }) {
  const rules = getPasswordChecklist(password);
  const hasTyped = password.length > 0;

  const items = [
    { ok: rules.length, label: '10–72 characters' },
    { ok: rules.upper, label: 'At least 1 uppercase letter' },
    { ok: rules.lower, label: 'At least 1 lowercase letter' },
    { ok: rules.digit, label: 'At least 1 number' },
    { ok: rules.symbol, label: 'At least 1 symbol' }
  ];

  return (
    <div className="password-rules-card" aria-live="polite">
      <div className="password-rules-title">Password requirements</div>
      <ul className="password-rules-list">
        {items.map((item) => (
          <li
            key={item.label}
            className={`password-rule ${item.ok ? 'is-ok' : hasTyped ? 'is-pending' : 'is-idle'}`}
          >
            <span className="password-rule-icon" aria-hidden="true">
              {item.ok ? '✓' : '○'}
            </span>
            <span>{item.label}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}