import React, { useMemo, useState } from 'react';
import { register } from '../api/authApi';
import { errorMessage } from '../utils';
import { validatePassword } from '../utils/passwordRules';
import PasswordRequirements from '../components/PasswordRequirements';

type FieldProps = {
  id: string;
  label: string;
  required?: boolean;
  error?: string | null;
  children: React.ReactNode;
};

function Field(props: FieldProps) {
  return (
    <div className="form-row">
      <label htmlFor={props.id}>
        {props.label} {props.required ? <span>*</span> : null}
      </label>

      <div>
        {props.children}
        {props.error ? <div className="field-error">{props.error}</div> : null}
      </div>
    </div>
  );
}

export default function RegisterPage(props: {
  onSuccess: () => void;
  onBack: () => void;
  onError: (msg: string) => void;
}) {
  const [email, setEmail] = useState('yang@example.com');

  const [password, setPassword] = useState('');
  const [repeatPassword, setRepeatPassword] = useState('');

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');

  const [address, setAddress] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [phone, setPhone] = useState('');

  const [loading, setLoading] = useState(false);

  const required = (v: string) => (v.trim() ? null : 'Required');

  const passwordValidation = validatePassword(password);

  const passwordError =
    password.length > 0 && !passwordValidation.isValid
      ? passwordValidation.errors[0]
      : null;

  const passwordMatchError =
    repeatPassword.length > 0 && password !== repeatPassword
      ? 'Passwords do not match'
      : null;

  const emailError = required(email);
  const firstNameError = required(firstName);
  const lastNameError = required(lastName);
  const cityError = required(city);
  const stateError = required(state);

  const canSubmit = useMemo(() => {
    return (
      !loading &&
      !emailError &&
      !firstNameError &&
      !lastNameError &&
      !cityError &&
      !stateError &&
      passwordValidation.isValid &&
      !passwordMatchError &&
      password.trim().length > 0 &&
      repeatPassword.trim().length > 0
    );
  }, [
    loading,
    emailError,
    firstNameError,
    lastNameError,
    cityError,
    stateError,
    passwordValidation.isValid,
    passwordMatchError,
    password,
    repeatPassword
  ]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;

    setLoading(true);
    try {
      await register({
        email: email.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        address: address.trim() ? address.trim() : null,
        city: city.trim(),
        state: state.trim().toUpperCase(),
        phone: phone.trim() ? phone.trim() : null
      });

      props.onSuccess();
    } catch (err: unknown) {
      props.onError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="panel-card">
      <div className="panel-header">
        <h3>Register</h3>
        <p>Create an account to track refund status and manage your profile.</p>
      </div>

      <form onSubmit={submit} className="form-grid">
        <Field id="reg-email" label="Email" required error={emailError}>
          <input
            id="reg-email"
            className="input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
          />
        </Field>

        <Field id="reg-password" label="Password" required error={passwordError}>
          <input
            id="reg-password"
            className="input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
          />

          <PasswordRequirements password={password} />
        </Field>

        <Field id="reg-repeat-password" label="Repeat Password" required error={passwordMatchError}>
          <input
            id="reg-repeat-password"
            className="input"
            type="password"
            value={repeatPassword}
            onChange={(e) => setRepeatPassword(e.target.value)}
            autoComplete="new-password"
          />
        </Field>

        <Field id="reg-first-name" label="First Name" required error={firstNameError}>
          <input
            id="reg-first-name"
            className="input"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            autoComplete="given-name"
          />
        </Field>

        <Field id="reg-last-name" label="Last Name" required error={lastNameError}>
          <input
            id="reg-last-name"
            className="input"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            autoComplete="family-name"
          />
        </Field>

        <Field id="reg-address" label="Address">
          <input
            id="reg-address"
            className="input"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            autoComplete="street-address"
          />
        </Field>

        <Field id="reg-city" label="City" required error={cityError}>
          <input
            id="reg-city"
            className="input"
            value={city}
            onChange={(e) => setCity(e.target.value)}
            autoComplete="address-level2"
          />
        </Field>

        <Field id="reg-state" label="State" required error={stateError}>
          <input
            id="reg-state"
            className="input"
            value={state}
            onChange={(e) => setState(e.target.value.toUpperCase())}
            maxLength={2}
            autoComplete="address-level1"
          />
        </Field>

        <Field id="reg-phone" label="Phone">
          <input
            id="reg-phone"
            className="input"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            autoComplete="tel"
          />
        </Field>

        <div className="form-actions">
          <button className="btn btn-primary btn-fixed-md" disabled={!canSubmit}>
            {loading ? 'Registering...' : 'Register'}
          </button>

          <button type="button" className="btn btn-secondary" onClick={props.onBack}>
            Back
          </button>
        </div>
      </form>
    </div>
  );
}