import React, { useEffect, useState } from 'react';
import { checkHealth } from '../api/dictionaryApi';
import { getApiBaseUrl } from '../config/apiConfig';
import { BookOpen, Settings, X, Wifi, WifiOff, HelpCircle, Save, Layers, Layers3 } from 'lucide-react';

export default function Header() {
  const [healthStatus, setHealthStatus] = useState('checking'); // checking, online, offline

  const checkConnection = async (urlToTest = getApiBaseUrl()) => {
    try {
      const response = await fetch(`${urlToTest}/api/dictionary/health`, {
        headers: {
          'ngrok-skip-browser-warning': 'true'
        }
      });
      if (response.ok) {
        return true;
      }
      return false;
    } catch (err) {
      return false;
    }
  };

  const verifyHealth = () => {
    setHealthStatus('checking');
    checkConnection()
      .then((ok) => {
        setHealthStatus(ok ? 'online' : 'offline');
      });
  };

  useEffect(() => {
    verifyHealth();
    // Re-verify periodically
    const interval = setInterval(() => {
      verifyHealth();
    }, 15000);
    return () => clearInterval(interval);
  }, []);

  return (
    <>
      <header className="app-header" id="app-header">
        <div className="header-container">
          <div className="logo-group">
            <div className="logo-icon-wrapper">
              <BookOpen className="logo-icon" size={24} />
            </div>
            <h1 className="logo-title">AI Dictionary</h1>
          </div>

          <nav className="header-nav">
            <div className="health-status-container">
              {healthStatus === 'checking' && (
                <div className="status-badge status-checking">
                  <span className="status-dot dot-checking"></span>
                  <span className="status-text">Đang kết nối...</span>
                </div>
              )}
              {healthStatus === 'online' && (
                <div className="status-badge status-online">
                  <span className="status-dot dot-online"></span>
                  <span className="status-text">Đã kết nối</span>
                </div>
              )}
              {healthStatus === 'offline' && (
                <div className="status-badge status-offline">
                  <span className="status-dot dot-offline"></span>
                  <span className="status-text">Mất kết nối</span>
                </div>
              )}
            </div>
          </nav>
        </div>
      </header>
    </>
  );
}
