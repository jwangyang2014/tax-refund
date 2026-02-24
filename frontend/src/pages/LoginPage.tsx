import React, { useState } from 'react';
import { login } from '../api/authApi';
import { errorMessage } from '../utils';

export default function LoginPage(props: {
  onSuccess: () => void;
  onRegister: () => void;
  onError: (msg: string) => void
}) {
  const [email, setEmail] = useState('yang@example.com');
  const [password, setPassword] = useState('Password@123#!');
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);

    try {
      await login(email, password);
      props.onSuccess();
    } catch (err: unknown) {
      props.onError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="panel" style={{ maxWidth: 520 }}>
      <div className="panel-header">
        <h3>Login</h3>
        <p className="muted">Sign in to check refund status and ask the assistant.</p>
      </div>

      <form onSubmit={submit} className="form-grid">
        <div>
          <label className="form-label" htmlFor="login-email">Email</label>
          <input
            id="login-email"
            className="input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="email"
            autoComplete="email"
          />
        </div>

        <div>
          <label className="form-label" htmlFor="login-password">Password</label>
          <input
            id="login-password"
            className="input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="password"
            type="password"
            autoComplete="current-password"
          />
        </div>

        <div className="form-actions" style={{ paddingLeft: 0 }}>
          <button className="btn btn-primary" disabled={loading}>
            {loading ? 'Signing in...' : 'Login'}
          </button>
          <button type="button" className="btn" onClick={props.onRegister}>
            Register
          </button>
        </div>
      </form>
    </div>
  );
}