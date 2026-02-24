import { useCallback, useEffect, useState } from "react";
import { logout, me } from "./api/authApi";
import ErrorBanner from "./components/ErrorBanner";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import ProfilePage from "./pages/ProfilePage";
import SecurityPage from "./pages/SecurityPage";
import "./styles/app.css";

type Screen = 'login' | 'register' | 'app';
type MainTab = 'refund' | 'profile' | 'security';

export default function App() {
  const [screen, setScreen] = useState<Screen>('login');
  const [tab, setTab] = useState<MainTab>('refund');
  const [error, setError] = useState<string | null>(null);

  const handleError = useCallback((message: string) => {
    setError(message);
  }, []);

  const goLogin = useCallback(() => {
    setError(null);
    setScreen('login');
  }, []);

  const goRegister = useCallback(() => {
    setError(null);
    setScreen('register');
  }, []);

  const goApp = useCallback(() => {
    setError(null);
    setTab('refund'); // landing tab
    setScreen('app');
  }, []);

  useEffect(() => {
    (async () => {
      try {
        await me();
        setScreen('app');
        setTab('refund');
      } catch {
        setScreen('login');
      }
    })();
  }, []);

  const doLogout = useCallback(async () => {
    try {
      await logout();
    } finally {
      setError(null);
      setScreen('login');
      setTab('refund');
    }
  }, []);

  return (
    <div className="app-shell">
      <div className="app-container">
        <header className="app-header">
          <div>
            <h2 className="app-title">TurboTax Refund Status Demo</h2>
            <p className="app-subtitle">Refund tracking, profile management, and AI assistance</p>
          </div>

          {screen === 'app' ? (
            <button className="btn btn-secondary" onClick={doLogout}>Logout</button>
          ) : null}
        </header>

        <ErrorBanner message={error} />

        {screen === 'login' ? (
          <LoginPage onSuccess={goApp} onRegister={goRegister} onError={handleError} />
        ) : null}

        {screen === 'register' ? (
          <RegisterPage onSuccess={goLogin} onBack={goLogin} onError={handleError} />
        ) : null}

        {screen === 'app' ? (
          <div className="main-tabs-shell">
            <div className="tabs-row" role="tablist" aria-label="Main navigation">
              <button
                role="tab"
                aria-selected={tab === 'refund'}
                className={`tab-btn ${tab === 'refund' ? 'active' : ''}`}
                onClick={() => setTab('refund')}
              >
                Refund Status
              </button>
              <button
                role="tab"
                aria-selected={tab === 'profile'}
                className={`tab-btn ${tab === 'profile' ? 'active' : ''}`}
                onClick={() => setTab('profile')}
              >
                Profile
              </button>
              <button
                role="tab"
                aria-selected={tab === 'security'}
                className={`tab-btn ${tab === 'security' ? 'active' : ''}`}
                onClick={() => setTab('security')}
              >
                Security
              </button>
            </div>

            <div className="tab-panel">
              {tab === 'refund' && <DashboardPage onError={handleError} />}
              {tab === 'profile' && <ProfilePage onError={handleError} />}
              {tab === 'security' && <SecurityPage onError={handleError} />}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}