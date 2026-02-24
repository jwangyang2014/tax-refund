// src/App.tsx
import { useCallback, useEffect, useState } from "react";
import { logout, me } from "./api/authApi";
import type { MeResponse } from "./api/types";
import ErrorBanner from "./components/ErrorBanner";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import ProfilePage from "./pages/ProfilePage";
import "./styles/app.css";

type Screen = "login" | "register" | "main";
type MainTab = "refund" | "profile";

export default function App() {
  const [screen, setScreen] = useState<Screen>("login");
  const [activeTab, setActiveTab] = useState<MainTab>("refund");
  const [error, setError] = useState<string | null>(null);
  const [sessionUser, setSessionUser] = useState<MeResponse | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);

  const handleError = useCallback((message: string) => {
    setError(message);
  }, []);

  const goLogin = useCallback(() => {
    setError(null);
    setScreen("login");
  }, []);

  const goRegister = useCallback(() => {
    setError(null);
    setScreen("register");
  }, []);

  const goMain = useCallback(async () => {
    setError(null);
    try {
      const user = await me();
      setSessionUser(user);
      setActiveTab("refund");
      setScreen("main");
    } catch {
      setScreen("login");
    }
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const user = await me();
        setSessionUser(user);
        setActiveTab("refund");
        setScreen("main");
      } catch {
        setScreen("login");
      } finally {
        setCheckingSession(false);
      }
    })();
  }, []);

  const doLogout = useCallback(async () => {
    try {
      await logout();
    } finally {
      setSessionUser(null);
      setError(null);
      setScreen("login");
      setActiveTab("refund");
    }
  }, []);

  if (checkingSession) {
    return (
      <div className="app-shell">
        <div className="app-card">Loading...</div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <div className="app-card">
        <header className="app-header">
          <div>
            <h1 className="app-title">TurboTax Refund Status</h1>
            <p className="app-subtitle">Interview Demo</p>
          </div>

          {screen === "main" && (
            <div className="header-actions">
              {sessionUser ? (
                <span className="chip">{sessionUser.email}</span>
              ) : null}
              <button className="btn btn-secondary" onClick={doLogout}>
                Logout
              </button>
            </div>
          )}
        </header>

        <ErrorBanner message={error} />

        {screen === "login" && (
          <LoginPage
            onSuccess={goMain}
            onRegister={goRegister}
            onError={handleError}
          />
        )}

        {screen === "register" && (
          <RegisterPage
            onSuccess={goLogin}
            onBack={goLogin}
            onError={handleError}
          />
        )}

        {screen === "main" && (
          <>
            <nav className="tabs">
              <button
                className={`tab ${activeTab === "refund" ? "tab-active" : ""}`}
                onClick={() => setActiveTab("refund")}
              >
                Refund Status
              </button>
              <button
                className={`tab ${activeTab === "profile" ? "tab-active" : ""}`}
                onClick={() => setActiveTab("profile")}
              >
                Profile
              </button>
            </nav>

            <section className="tab-panel">
              {activeTab === "refund" ? (
                <DashboardPage onError={handleError} />
              ) : (
                <ProfilePage onError={handleError} />
              )}
            </section>
          </>
        )}
      </div>
    </div>
  );
}