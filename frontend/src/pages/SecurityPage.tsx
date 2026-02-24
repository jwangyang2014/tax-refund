import React, { useMemo, useState } from 'react';
import { changePassword } from '../api/authApi';
import { errorMessage } from '../utils';
import { validatePassword } from '../utils/passwordRules';
import PasswordRequirements from '../components/PasswordRequirements';

export default function SecurityPage(props: {
  onError: (msg: string) => void;
}) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [repeatPassword, setRepeatPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);

  const passwordValidation = validatePassword(newPassword);
  
  const newPasswordError =
    newPassword.length > 0 && !passwordValidation.isValid
      ? passwordValidation.errors[0]
      : null;

  const passwordMatchError =
    repeatPassword.length > 0 && newPassword !== repeatPassword
      ? 'Passwords do not match'
      : null;

  const canSubmit = useMemo(() => {
    return (
      !loading &&
      currentPassword.trim().length > 0 &&
      newPassword.trim().length > 0 &&
      repeatPassword.trim().length > 0 &&
      passwordValidation.isValid &&
      !passwordMatchError
    );
  }, [loading, currentPassword, newPassword, repeatPassword, passwordValidation.isValid, passwordMatchError]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSuccess(null);

    if (!canSubmit) return;

    setLoading(true);
    try {
      await changePassword(currentPassword, newPassword);

      setCurrentPassword('');
      setNewPassword('');
      setRepeatPassword('');
      setSuccess('Password updated successfully.');
    } catch (err: unknown) {
      props.onError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="panel-card">
      <div className="panel-header">
        <h3>Security</h3>
        <p>Change your password to keep your account secure.</p>
      </div>

      <form onSubmit={submit} className="form-grid">
        <div className="form-row">
          <label htmlFor="curr-password">Current Password</label>
          <div>
            <input
              id="curr-password"
              type="password"
              className="input"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>
        </div>

        <div className="form-row">
          <label htmlFor="new-password">New Password</label>
          <div>
            <input
              id="new-password"
              type="password"
              className="input"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
            />
            {newPasswordError ? <div className="field-error">{newPasswordError}</div> : null}

            <PasswordRequirements password={newPassword} />
          </div>
        </div>

        <div className="form-row">
          <label htmlFor="repeat-new-password">Repeat New Password</label>
          <div>
            <input
              id="repeat-new-password"
              type="password"
              className="input"
              value={repeatPassword}
              onChange={(e) => setRepeatPassword(e.target.value)}
              autoComplete="new-password"
            />
            {passwordMatchError ? <div className="field-error">{passwordMatchError}</div> : null}
          </div>
        </div>

        {success ? <div className="success-banner">{success}</div> : null}

        <div className="form-actions">
          <button className="btn btn-primary btn-fixed-md" type="submit" disabled={!canSubmit}>
            {loading ? 'Saving...' : 'Update Password'}
          </button>
        </div>
      </form>
    </div>
  );
}