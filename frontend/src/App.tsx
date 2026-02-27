import { useCallback, useEffect, useState } from "react";
import { logout, getSession } from "./api/authApi";
import { AUTH_EXPIRED_EVENT } from "./authEvents";
import ErrorBanner from "./components/ErrorBanner";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import ProfilePage from "./pages/ProfilePage";
import SecurityPage from "./pages/SecurityPage";
import "./styles/app.css";

type Screen = 'boot' | 'login' | 'register' | 'app';
type MainTab = 'refund' | 'profile' | 'security';

export default function App() {
  const [screen, setScreen] = useState<Screen>('boot');
  const [tab, setTab] = useState<MainTab>('refund');
  const [error, setError] = useState<string | null>(null);

  const handleError = useCallback((message: string | null) => {
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
    setTab('refund');
    setScreen('app');
  }, []);

  useEffect(() => {
    let mounted = true;

    (async () => {
      try {
        await getSession(); // silent auth check
        if (!mounted) return;
        setError(null);
        setTab('refund');
        setScreen('app');
      } catch {
        // expected when not logged in — do NOT show error
        if (!mounted) return;
        setError(null);
        setScreen('login');
      }
    })();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    function handleAuthExpired() {
      setError("Your session has expired. Please log in again.");
      setTab('refund');
      setScreen('login');
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);

    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    };
  }, []);

  const doLogout = useCallback(async () => {
    try {
      await logout();
    } finally {
      setError(null);
      setTab('refund');
      setScreen('login');
    }
  }, []);

  if (screen === 'boot') {
    return (
      <div className="app-shell">
        <div className="app-container">
          <div className="panel-card">Loading...</div>
        </div>
      </div>
    );
  }

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

        {screen === 'login' && (
          <LoginPage onSuccess={goApp} onRegister={goRegister} onError={handleError} />
        )}

        {screen === 'register' && (
          <RegisterPage onSuccess={goLogin} onBack={goLogin} onError={handleError} />
        )}

        {screen === 'app' && (
          <div className="main-tabs-shell">
            <div className="tabs-row" role="tablist" aria-label="Main navigation">
              <button
                role="tab"
                aria-selected={tab === 'refund'}
                className={`tab-btn ${tab === 'refund' ? 'active' : ''}`}
                onClick={() => {
                  setError(null);
                  setTab('refund');
                }}
              >
                Refund Status
              </button>
              <button
                role="tab"
                aria-selected={tab === 'profile'}
                className={`tab-btn ${tab === 'profile' ? 'active' : ''}`}
                onClick={() => {
                  setError(null);
                  setTab('profile');
                }}
              >
                Profile
              </button>
              <button
                role="tab"
                aria-selected={tab === 'security'}
                className={`tab-btn ${tab === 'security' ? 'active' : ''}`}
                onClick={() => {
                  setError(null);
                  setTab('security');
                }}
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
        )}
      </div>
    </div>
  );
}