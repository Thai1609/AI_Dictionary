import React from 'react';
import { ChevronDown, ChevronUp, Check, Hash } from 'lucide-react';
import ResultCard from './ResultCard';

export default function WordOptionCard({ 
  option, 
  isSelected, 
  isLoading, 
  details, 
  onToggle 
}) {
  return (
    <div 
      className={`word-option-card ${isSelected ? 'selected' : ''}`}
      style={{
        padding: '1.25rem',
        backgroundColor: 'var(--color-bg-card)',
        borderRadius: '12px',
        border: `1px solid ${isSelected ? 'var(--color-primary)' : 'var(--color-border)'}`,
        boxShadow: isSelected ? '0 4px 6px rgba(0,0,0,0.1)' : '0 1px 3px rgba(0,0,0,0.05)',
        transition: 'all 0.2s ease',
        marginBottom: '1rem',
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              <h3 style={{ fontSize: '1.5rem', fontWeight: '700', color: 'var(--color-primary)', margin: 0 }}>
                {option.word}
              </h3>
              {option.reading && (
                <span style={{ color: 'var(--color-text-light)', fontSize: '1rem' }}>
                  [{option.reading}]
                </span>
              )}
              {option.partOfSpeech && (
                <span className="part-of-speech-badge" style={{ fontSize: '0.85rem' }}>
                  {option.partOfSpeech}
                </span>
              )}
            </div>
            {option.meanings && option.meanings.length > 0 && (
              <p style={{ color: 'var(--color-text)', margin: '0.5rem 0 0 0', fontSize: '1.05rem', fontWeight: '500' }}>
                {option.meanings.join(', ')}
              </p>
            )}
          </div>

          <button
            onClick={() => onToggle(option)}
            disabled={isLoading}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.5rem 1rem',
              backgroundColor: isSelected ? 'var(--color-bg-hover)' : 'var(--color-primary)',
              color: isSelected ? 'var(--color-primary)' : 'white',
              border: 'none',
              borderRadius: '8px',
              cursor: isLoading ? 'not-allowed' : 'pointer',
              fontSize: '0.9rem',
              fontWeight: '600',
              opacity: isLoading ? 0.7 : 1,
              whiteSpace: 'nowrap'
            }}
          >
            {isLoading ? (
              <span>Đang tải...</span>
            ) : isSelected ? (
              <>
                Thu gọn <ChevronUp size={18} />
              </>
            ) : (
              <>
                Xem chi tiết <ChevronDown size={18} />
              </>
            )}
          </button>
        </div>

        {option.usage && (
          <p style={{ margin: 0, fontSize: '0.95rem', color: 'var(--color-text-light)', fontStyle: 'italic' }}>
            <span style={{ fontWeight: '600', fontStyle: 'normal' }}>Cách dùng:</span> {option.usage}
          </p>
        )}

        {option.recommended && (
          <div style={{ 
            display: 'inline-flex', 
            alignItems: 'center', 
            gap: '0.25rem', 
            backgroundColor: 'rgba(16, 185, 129, 0.1)', 
            color: '#10b981', 
            padding: '0.35rem 0.75rem', 
            borderRadius: '6px',
            fontSize: '0.85rem',
            fontWeight: '600',
            alignSelf: 'flex-start',
            marginTop: '0.25rem'
          }}>
            <Check size={14} /> 
            <span>Đề xuất</span>
            {option.reason && <span style={{ fontWeight: '400', marginLeft: '0.25rem' }}>- {option.reason}</span>}
          </div>
        )}
      </div>

      {isSelected && details && (
        <div style={{ marginTop: '1.5rem', paddingTop: '1.5rem', borderTop: '1px dashed var(--color-border)' }}>
          {/* Use ResultCard or similar to show details, since ResultCard expects a specific format */}
          {/* We can just render the details directly here if ResultCard is too bulky, but ResultCard works well for dictionary entries */}
          <ResultCard 
            result={{ type: 'word', dictionary: details }}
            mode="word" 
            sourceLanguage="" 
            targetLanguage="" 
            inputText={option.word}
          />
        </div>
      )}
    </div>
  );
}
