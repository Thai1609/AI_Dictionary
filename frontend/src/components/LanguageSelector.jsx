import React from 'react';
import { ArrowRightLeft } from 'lucide-react';

export default function LanguageSelector({
  sourceLanguage,
  setSourceLanguage,
  targetLanguage,
  setTargetLanguage,
  disabled,
  mode
}) {
  const languages = [
    { code: 'zh', label: 'Chinese' },
    { code: 'vi', label: 'Vietnamese' },
  ];

  const handleSwap = () => {
    if (disabled) return;
    // Swap source and target languages
    const temp = sourceLanguage;
    setSourceLanguage(targetLanguage);
    setTargetLanguage(temp);
  };

  const handleSourceChange = (e) => {
    const newSource = e.target.value;
    if (newSource === targetLanguage) {
      setTargetLanguage(sourceLanguage);
    }
    setSourceLanguage(newSource);
  };

  const handleTargetChange = (e) => {
    const newTarget = e.target.value;
    if (newTarget === sourceLanguage) {
      setSourceLanguage(targetLanguage);
    }
    setTargetLanguage(newTarget);
  };

  if (mode === 'search') {
    return null;
  }

  return (
    <div className="language-selector" id="language-selector">
      <div className="lang-dropdown-group">
        <label htmlFor="source-language-select" className="lang-label">Nguồn</label>
        <select
          id="source-language-select"
          className="lang-select"
          value={sourceLanguage}
          onChange={handleSourceChange}
          disabled={disabled}
        >
          {languages.map((lang) => (
            <option key={lang.code} value={lang.code}>
              {lang.label}
            </option>
          ))}
        </select>
      </div>

      <button
        type="button"
        id="swap-language-btn"
        className="swap-btn"
        onClick={handleSwap}
        disabled={disabled}
        title="Đổi chiều ngôn ngữ"
      >
        <ArrowRightLeft size={16} />
      </button>

      <div className="lang-dropdown-group">
        <label htmlFor="target-language-select" className="lang-label">Đích</label>
        <select
          id="target-language-select"
          className="lang-select"
          value={targetLanguage}
          onChange={handleTargetChange}
          disabled={disabled}
        >
          {languages.map((lang) => (
            <option key={lang.code} value={lang.code}>
              {lang.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
