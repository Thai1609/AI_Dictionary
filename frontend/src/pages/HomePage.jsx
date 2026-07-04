import React, { useState } from 'react';
import Header from '../components/Header';
import ModeTabs from '../components/ModeTabs';
import LanguageSelector from '../components/LanguageSelector';
import SearchBox from '../components/SearchBox';
import ResultCard from '../components/ResultCard';
import { analyzeText, searchDictionary } from '../api/dictionaryApi';
import { getApiBaseUrl } from '../config/apiConfig';
import { AlertCircle, RefreshCw, HelpCircle, BookOpen, Layers } from 'lucide-react';

export default function HomePage() {
  const [text, setText] = useState('');
  const [mode, setMode] = useState('search'); // 'search' | 'word' | 'sentence' | 'grammar'
  const [sourceLanguage, setSourceLanguage] = useState('zh');
  const [targetLanguage, setTargetLanguage] = useState('vi');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [searchResults, setSearchResults] = useState(null);
  const [error, setError] = useState('');

  const handleAnalyze = async () => {
    if (!text.trim()) {
      if (mode === 'search') {
        setError('Vui lòng nhập từ khóa cần tìm.');
      } else {
        setError('Vui lòng nhập nội dung cần tra cứu!');
      }
      return;
    }

    setLoading(true);
    setError('');
    setResult(null);
    setSearchResults(null);

    try {
      if (mode === 'search') {
        const data = await searchDictionary(text.trim());
        setSearchResults(data);
      } else {
        const data = await analyzeText({
          text: text.trim(),
          mode,
          sourceLanguage,
          targetLanguage,
        });

        if (data) {
          setResult(data);
        } else {
          setError('Không nhận được dữ liệu phản hồi từ máy chủ.');
        }
      }
    } catch (err) {
      console.error('Lỗi khi thao tác:', err);
      const currentUrl = getApiBaseUrl();
      const isFailedToFetch = err.message && (err.message.includes('Failed to fetch') || err.message.includes('fetch') || err.message.includes('NetworkError'));
      
      if (isFailedToFetch) {
        setError(
          `Không thể kết nối đến máy chủ Spring Boot tại (${currentUrl}). Vui lòng đảm bảo ứng dụng Spring Boot đã chạy, đã kích hoạt CORS (@CrossOrigin), hoặc nhấn nút 'Cấu hình API' ở trên để cập nhật lại địa chỉ backend.`
        );
      } else {
        setError(err.message || `Lỗi không xác định khi kết nối đến máy chủ (${currentUrl}).`);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleModeChange = (newMode) => {
    setMode(newMode);
    // Optionally clear results when switching modes to avoid mismatched presentation
    setResult(null);
    setSearchResults(null);
    setError('');
  };

  return (
    <div className="homepage-container" id="homepage-container">
      <Header />
      
      <main className="main-content">
        <div className="control-panel">
          {/* Section: Mode Tabs Selection */}
          <ModeTabs
            activeMode={mode}
            onChangeMode={handleModeChange}
            disabled={loading}
          />

          {/* Section: Language Configuration */}
          <LanguageSelector
            sourceLanguage={sourceLanguage}
            setSourceLanguage={setSourceLanguage}
            targetLanguage={targetLanguage}
            setTargetLanguage={setTargetLanguage}
            disabled={loading}
            mode={mode}
          />

          {/* Section: Query Search Box */}
          <SearchBox
            text={text}
            setText={setText}
            onSubmit={handleAnalyze}
            loading={loading}
            mode={mode}
          />
        </div>

        {/* Section: Feedback States (Errors, Loading, Results) */}
        <div className="output-panel">
          {error && (
            <div className="error-alert-box" id="error-alert" style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', padding: '1.25rem', borderRadius: '12px' }}>
              <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-start' }}>
                <AlertCircle size={20} className="error-icon" style={{ marginTop: '0.15rem', flexShrink: 0 }} />
                <div className="error-content">
                  <h4 className="error-title" style={{ fontWeight: '700', fontSize: '1rem' }}>
                    {error.includes('404') ? 'Lỗi Endpoint Không Tìm Thấy (404 Not Found)' : 'Đã xảy ra lỗi kết nối'}
                  </h4>
                  <p className="error-desc" style={{ marginTop: '0.25rem', fontSize: '0.875rem', opacity: 0.9 }}>{error}</p>
                </div>
              </div>
              
              {error.includes('404') && (
                <div style={{
                  marginTop: '0.5rem',
                  padding: '1rem',
                  backgroundColor: 'rgba(239, 68, 68, 0.05)',
                  border: '1px dashed rgba(239, 68, 68, 0.25)',
                  borderRadius: '8px',
                  fontSize: '0.825rem',
                  color: '#4b5563',
                  lineHeight: '1.4'
                }}>
                  <p style={{ fontWeight: '600', color: '#dc2626', marginBottom: '0.5rem' }}>🔍 Nguyên nhân phổ biến lỗi 404 trong Spring Boot:</p>
                  <ul style={{ listStyleType: 'disc', paddingLeft: '1.25rem', display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                    <li>
                      <strong>Thiếu Mapping trên Controller:</strong> Hãy kiểm tra xem class Controller trong Spring Boot của bạn có annotation <code>@RequestMapping("/api/dictionary")</code> và phương thức xử lý có <code>@PostMapping("/analyze")</code> hay chưa.
                    </li>
                    <li>
                      <strong>Context Path:</strong> Nếu bạn cấu hình <code>server.servlet.context-path</code> trong <code>application.properties</code>, hãy chắc chắn đã cộng dồn nó vào cấu hình URL trong mục <strong>Cấu hình API</strong>.
                    </li>
                    <li>
                      <strong>Yêu cầu API cụ thể từ Frontend:</strong>
                      <ul style={{ listStyleType: 'circle', paddingLeft: '1rem', marginTop: '0.25rem', color: '#1f2937' }}>
                        <li>Kiểm tra trạng thái: <code>GET {getApiBaseUrl()}/api/dictionary/health</code></li>
                        <li>Phân tích từ/câu: <code>POST {getApiBaseUrl()}/api/dictionary/analyze</code></li>
                      </ul>
                    </li>
                  </ul>
                </div>
              )}
            </div>
          )}

          {loading && (
            <div className="loading-card" id="loading-indicator">
              <RefreshCw size={36} className="animate-spin loading-spinner" />
              <p className="loading-text">Đang phân tích dữ liệu qua AI...</p>
              <span className="loading-subtext">Hệ thống đang kết hợp Spring Boot & PostgreSQL</span>
            </div>
          )}

          {!loading && !result && !error && !searchResults && (
            <div className="welcome-placeholder" id="welcome-placeholder">
              <div className="placeholder-icon-wrapper">
                <BookOpen size={48} className="placeholder-icon" />
              </div>
              <h2 className="placeholder-title">Chào mừng đến với AI Dictionary</h2>
              <p className="placeholder-desc">
                Nhập từ khóa, câu hoặc đoạn văn cần chỉnh sửa ở trên, sau đó nhấn nút <strong>Tra cứu / Tìm kiếm</strong> để phân tích hoặc tìm trong database.
              </p>
              
              <div className="feature-grid">
                <div className="feature-item">
                  <span className="feature-badge badge-word" style={{backgroundColor: 'rgba(59, 130, 246, 0.12)', color: '#3b82f6'}}>Tìm trong DB</span>
                  <p className="feature-text">Tìm kiếm nhanh các từ vựng đã được lưu trữ trong cơ sở dữ liệu PostgreSQL.</p>
                </div>
                <div className="feature-item">
                  <span className="feature-badge badge-word">Tra từ</span>
                  <p className="feature-text">Giải nghĩa từ vựng, phát âm, từ loại, ví dụ câu, và lưu trữ trực tiếp vào PostgreSQL database.</p>
                </div>
                <div className="feature-item">
                  <span className="feature-badge badge-sentence">Phân tích câu</span>
                  <p className="feature-text">Dịch câu chi tiết, phân tích các cụm từ quan trọng và cấu trúc ngữ pháp có trong câu.</p>
                </div>
              </div>
            </div>
          )}

          {/* Render Search Results List */}
          {!loading && !result && searchResults && searchResults.length === 0 && !error && (
            <div className="search-results-empty animate-fade-in" style={{ marginTop: '2rem', padding: '1.25rem', backgroundColor: 'var(--color-bg-card)', borderRadius: '12px', border: '1px solid var(--color-border)', textAlign: 'center' }}>
              <p style={{ color: 'var(--color-text-light)', fontSize: '1.05rem' }}>Không tìm thấy từ trong database.</p>
            </div>
          )}

          {!loading && !result && searchResults && searchResults.length > 0 && (
            <div className="search-results-list animate-fade-in" style={{ marginTop: '2rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: '600', color: 'var(--color-text)', marginBottom: '0.5rem' }}>Tìm thấy {searchResults.length} kết quả</h3>
              {searchResults.map((item, index) => (
                <div 
                  key={index} 
                  className="search-result-item" 
                  style={{ 
                    padding: '1.25rem', 
                    backgroundColor: 'var(--color-bg-card)', 
                    borderRadius: '12px', 
                    border: '1px solid var(--color-border)',
                    cursor: 'pointer',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                    transition: 'all 0.2s ease',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.5rem'
                  }}
                  onClick={() => setResult({ type: 'word', dictionary: item })}
                  onMouseOver={(e) => {
                    e.currentTarget.style.borderColor = 'var(--color-primary)';
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = '0 4px 6px rgba(0,0,0,0.05)';
                  }}
                  onMouseOut={(e) => {
                    e.currentTarget.style.borderColor = 'var(--color-border)';
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.05)';
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.75rem', flexWrap: 'wrap' }}>
                    <h4 style={{ fontSize: '1.3rem', fontWeight: '700', color: 'var(--color-primary)', margin: 0 }}>{item.word}</h4>
                    {item.pronunciation && <span style={{ color: 'var(--color-text-light)', fontSize: '0.95rem' }}>[{item.pronunciation}]</span>}
                    {item.partOfSpeech && (
                      <span className="part-of-speech-badge" style={{ marginLeft: 'auto' }}>{item.partOfSpeech}</span>
                    )}
                  </div>
                  {item.meanings && item.meanings.length > 0 && (
                    <p style={{ color: 'var(--color-text)', margin: 0, fontSize: '1rem', lineHeight: '1.5' }}>
                      <span style={{ fontWeight: '600', marginRight: '0.5rem' }}>Nghĩa:</span>
                      {item.meanings[0]}
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* Render Result Cards */}
          {!loading && result && (
            <div className="result-container animate-fade-in" id="result-container">
              {mode === 'search' && searchResults && (
                <button 
                  onClick={() => setResult(null)}
                  style={{
                    marginBottom: '1.25rem',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                    padding: '0.5rem 1rem',
                    backgroundColor: 'var(--color-bg-card)',
                    border: '1px solid var(--color-border)',
                    borderRadius: '8px',
                    color: 'var(--color-text-light)',
                    cursor: 'pointer',
                    fontSize: '0.9rem',
                    fontWeight: '500',
                    transition: 'all 0.2s',
                    boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
                  }}
                  onMouseOver={(e) => { e.currentTarget.style.backgroundColor = 'var(--color-bg-hover)'; e.currentTarget.style.color = 'var(--color-text)'; }}
                  onMouseOut={(e) => { e.currentTarget.style.backgroundColor = 'var(--color-bg-card)'; e.currentTarget.style.color = 'var(--color-text-light)'; }}
                >
                  ← Quay lại danh sách
                </button>
              )}
              <ResultCard
                result={result}
                sourceLanguage={sourceLanguage}
                targetLanguage={targetLanguage}
                inputText={text}
                mode={mode}
              />
            </div>
          )}
        </div>
      </main>

      <footer className="footer-credits">
        <p>© 2026 AI Dictionary App. Kết nối Spring Boot + PostgreSQL.</p>
      </footer>
    </div>
  );
}
