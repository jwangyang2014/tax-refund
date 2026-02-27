import React, { useEffect, useMemo, useState } from "react";
import { getProfile, updateProfile } from "../api/profileApi";
import type { UserProfile } from "../api/types";
import { errorMessage } from "../utils";

function Field(props: {
  label: string;
  htmlFor: string;
  required?: boolean;
  error?: string | null;
  children: React.ReactNode;
}) {
  return (
    <div className="form-row">
      <label htmlFor={props.htmlFor} className="form-label">
        {props.label} {props.required ? <span className="required">*</span> : null}
      </label>
      <div>
        {props.children}
        {props.error ? <div className="field-error">{props.error}</div> : null}
      </div>
    </div>
  );
}

export default function ProfilePage({ onError }: { onError: (msg: string | null) => void }) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);

  const [profile, setProfile] = useState<UserProfile | null>(null);

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [address, setAddress] = useState("");
  const [city, setCity] = useState("");
  const [state, setState] = useState("");
  const [phone, setPhone] = useState("");

  useEffect(() => {
    (async () => {
      setLoading(true);
      setSavedMsg(null);
      onError(null);

      try {
        const p = await getProfile();
        setProfile(p);
        setFirstName(p.firstName ?? "");
        setLastName(p.lastName ?? "");
        setAddress(p.address ?? "");
        setCity(p.city ?? "");
        setState((p.state ?? "").toUpperCase());
        setPhone(p.phone ?? "");
        onError(null);
      } catch (e: unknown) {
        onError(errorMessage(e));
      } finally {
        setLoading(false);
      }
    })();
  }, [onError]);

  const required = (v: string) => (v.trim() ? null : "Required");

  const firstNameError = required(firstName);
  const lastNameError = required(lastName);
  const cityError = required(city);
  const stateError =
    !state.trim() ? "Required" : state.trim().length !== 2 ? "Use 2-letter state code" : null;

  const canSave = useMemo(() => {
    return !saving && !firstNameError && !lastNameError && !cityError && !stateError && !!profile;
  }, [saving, firstNameError, lastNameError, cityError, stateError, profile]);

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    if (!canSave || !profile) return;

    setSaving(true);
    setSavedMsg(null);
    onError(null);

    try {
      const updated = await updateProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        address: address.trim() ? address.trim() : null,
        city: city.trim(),
        state: state.trim().toUpperCase(),
        phone: phone.trim() ? phone.trim() : null,
      });

      setProfile(updated);
      setSavedMsg("Profile updated successfully.");
      onError(null);
    } catch (e: unknown) {
      onError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  function clearBannerError() {
    onError(null);
    if (savedMsg) setSavedMsg(null);
  }

  if (loading) {
    return <div className="panel">Loading profile...</div>;
  }

  if (!profile) {
    return <div className="panel">Unable to load profile.</div>;
  }

  return (
    <div className="panel">
      <div className="panel-header">
        <div>
          <h3>Profile</h3>
          <p className="muted">Manage your contact details used for refund support and notifications.</p>
        </div>
      </div>

      <form onSubmit={onSave} className="form-grid">
        <Field label="Email" htmlFor="profile-email">
          <input id="profile-email" value={profile.email} disabled className="input" />
        </Field>

        <Field label="Role" htmlFor="profile-role">
          <input id="profile-role" value={profile.role} disabled className="input" />
        </Field>

        <Field label="First Name" htmlFor="profile-firstName" required error={firstNameError}>
          <input
            id="profile-firstName"
            className="input"
            value={firstName}
            onChange={(e) => {
              clearBannerError();
              setFirstName(e.target.value);
            }}
          />
        </Field>

        <Field label="Last Name" htmlFor="profile-lastName" required error={lastNameError}>
          <input
            id="profile-lastName"
            className="input"
            value={lastName}
            onChange={(e) => {
              clearBannerError();
              setLastName(e.target.value);
            }}
          />
        </Field>

        <Field label="Address" htmlFor="profile-address">
          <input
            id="profile-address"
            className="input"
            value={address}
            onChange={(e) => {
              clearBannerError();
              setAddress(e.target.value);
            }}
          />
        </Field>

        <Field label="City" htmlFor="profile-city" required error={cityError}>
          <input
            id="profile-city"
            className="input"
            value={city}
            onChange={(e) => {
              clearBannerError();
              setCity(e.target.value);
            }}
          />
        </Field>

        <Field label="State" htmlFor="profile-state" required error={stateError}>
          <input
            id="profile-state"
            className="input"
            maxLength={2}
            value={state}
            onChange={(e) => {
              clearBannerError();
              setState(e.target.value.toUpperCase());
            }}
          />
        </Field>

        <Field label="Phone" htmlFor="profile-phone">
          <input
            id="profile-phone"
            className="input"
            value={phone}
            onChange={(e) => {
              clearBannerError();
              setPhone(e.target.value);
            }}
          />
        </Field>

        <div className="form-actions">
          <button className="btn btn-primary" disabled={!canSave}>
            {saving ? "Saving..." : "Save Profile"}
          </button>
          {savedMsg ? <span className="success-text">{savedMsg}</span> : null}
        </div>
      </form>
    </div>
  );
}