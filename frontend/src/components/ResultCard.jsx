import React from 'react';
import SaveButton from './SaveButton';
import { BookOpen, Info, Hash, Play, Link, Lightbulb, MessageSquare } from 'lucide-react';

export default function ResultCard({ result, sourceLanguage, targetLanguage, inputText, mode }) {
  if (!result) return null;

  // Helper function to extract data robustly from any backend structure
  const getExtractedData = (res) => {
    if (!res) return { type: '', source: {} };

    // 1. If wrapped in an outer 'data' object (e.g. standard API response wrappers)
    let target = res;
    if (res.data && typeof res.data === 'object' && !Array.isArray(res.data)) {
      target = res.data;
    }

    // 2. Determine type
    let determinedType = res.type || target.type || '';

    // 3. Find the dictionary source
    let source = target.dictionary || target.grammar || target;

    if (!determinedType) {
      if (source.word || target.word || res.word) {
        determinedType = 'word';
      } else if (source.originalSentence || target.originalSentence || res.originalSentence) {
        determinedType = 'sentence';
      } else if (source.correctedText || target.correctedText || res.correctedText) {
        determinedType = 'grammar';
      }
    }

    // Ensure we fallback to target or res if source doesn't have key fields
    if (determinedType === 'word' && !source.word) {
      if (target.word) source = target;
      else if (res.word) source = res;
    } else if (determinedType === 'sentence' && !source.originalSentence) {
      if (target.originalSentence) source = target;
      else if (res.originalSentence) source = res;
    } else if (determinedType === 'grammar' && !source.correctedText && !source.originalText) {
      if (target.correctedText || target.originalText) source = target;
      else if (res.correctedText || res.originalText) source = res;
    }

    return { type: determinedType, source };
  };

  const { type, source: dictionarySource } = getExtractedData(result);

  if (type === 'word') {
    const {
      word,
      pronunciation,
      reading,
      partOfSpeech,
      meanings = [],
      examples = [],
      relatedWords = [],
      note
    } = dictionarySource;

    const finalWord = word || result?.text || 'N/A';

    return (
      <div className="result-card word-result" id="result-card-word">
        <div className="card-header">
          <div className="word-title-row">
            <h2 className="word-title">{finalWord}</h2>
            {partOfSpeech && (
              <span className="part-of-speech-badge">Từ loại: {partOfSpeech}</span>
            )}
          </div>
          {dictionarySource?.pronunciation && (
            <div className="pronunciation-row">
              <span className="pronunciation-label" style={{ fontWeight: 600, marginRight: '0.5rem', color: 'var(--color-text-light)' }}>
                {sourceLanguage === 'zh' ? 'Pinyin:' : 'Phiên âm:'}
              </span>
              <span className="pronunciation-text">{dictionarySource.pronunciation}</span>
            </div>
          )}
        </div>

        <div className="card-body">
          {/* Meanings */}
          {meanings && meanings.length > 0 && (
            <div className="result-section">
              <h3 className="section-title">
                <BookOpen size={16} className="section-icon" />
                Nghĩa của từ
              </h3>
              <ul className="meaning-list">
                {meanings.map((meaning, index) => (
                  <li key={index} className="meaning-item">
                    <span className="bullet-number">{index + 1}</span>
                    <span className="meaning-text">{meaning}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Examples */}
          {examples && examples.length > 0 && (
            <div className="result-section">
              <h3 className="section-title">
                <Play size={16} className="section-icon" />
                Ví dụ sử dụng
              </h3>
              <div className="example-list">
                {examples?.map((ex, index) => (
                  <div key={index} className="example-item">
                    <p className="example-sentence">{ex?.sentence}</p>
                    {ex?.reading ? (
                      <p className="example-pinyin" style={{ color: 'var(--color-primary)', fontSize: '0.9rem', marginBottom: '0.2rem' }}>{ex.reading}</p>
                    ) : null}
                    {ex?.translation && (
                      <p className="example-translation">{ex.translation}</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Related Words */}
          {relatedWords && relatedWords.length > 0 && (
            <div className="result-section">
              <h3 className="section-title">
                <Link size={16} className="section-icon" />
                Từ liên quan
              </h3>
              <div className="related-tags">
                {relatedWords.map((related, index) => (
                  <span key={index} className="related-tag">
                    {related}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Notes */}
          {note && (
            <div className="result-section note-section">
              <h3 className="section-title">
                <Info size={16} className="section-icon" />
                Ghi chú
              </h3>
              <p className="note-text">{note}</p>
            </div>
          )}
        </div>

        {/* Footer with Save to DB button */}
        {mode !== 'search' && (
          <div className="card-footer">
            <SaveButton
              type={result?.type}
              sourceLanguage={sourceLanguage}
              targetLanguage={targetLanguage}
              searchKeyword={inputText}
              dictionary={result?.dictionary}
            />
          </div>
        )}
      </div>
    );
  }

  if (type === 'sentence') {
    const {
      originalSentence,
      translation,
      naturalVersion,
      keyPhrases = [],
      grammarPoints = [],
      note
    } = dictionarySource;

    return (
      <div className="result-card sentence-result" id="result-card-sentence">
        <div className="card-header sentence-header">
          <h2 className="sentence-badge-title">Phân tích câu</h2>
        </div>

        <div className="card-body">
          {/* Original Sentence */}
          <div className="result-section highlighted-section">
            <h3 className="section-title">
              <MessageSquare size={16} className="section-icon" />
              Câu gốc
            </h3>
            <p className="original-sentence-text">{originalSentence}</p>
          </div>

          {/* Translation */}
          {translation && (
            <div className="result-section">
              <h3 className="section-title">
                <BookOpen size={16} className="section-icon" />
                Bản dịch
              </h3>
              <p className="translation-text">{translation}</p>
            </div>
          )}

          {/* Natural Version */}
          {naturalVersion && (
            <div className="result-section natural-section">
              <h3 className="section-title">
                <Lightbulb size={16} className="section-icon text-amber" />
                Cách diễn đạt tự nhiên hơn
              </h3>
              <p className="natural-text">{naturalVersion}</p>
            </div>
          )}

          {/* Key Phrases */}
          {keyPhrases && keyPhrases.length > 0 && (
            <div className="result-section">
              <h3 className="section-title">
                <Hash size={16} className="section-icon" />
                Cụm từ then chốt
              </h3>
              <div className="key-phrases-list">
                {keyPhrases.map((phraseObj, index) => (
                  <div key={index} className="key-phrase-item">
                    <div className="phrase-header">
                      <span className="phrase-bold">{phraseObj.phrase}</span>
                      {phraseObj.meaning && (
                        <span className="phrase-meaning">: {phraseObj.meaning}</span>
                      )}
                    </div>
                    {phraseObj.note && (
                      <p className="phrase-note">{phraseObj.note}</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Grammar Points */}
          {grammarPoints && grammarPoints.length > 0 && (
            <div className="result-section">
              <h3 className="section-title">
                <Info size={16} className="section-icon" />
                Cấu trúc ngữ pháp
              </h3>
              <div className="grammar-points-list">
                {grammarPoints.map((gp, index) => (
                  <div key={index} className="grammar-point-item">
                    <p className="pattern-text">
                      <strong>Cấu trúc:</strong> {gp.pattern}
                    </p>
                    {gp.meaning && (
                      <p className="pattern-meaning">
                        <strong>Ý nghĩa:</strong> {gp.meaning}
                      </p>
                    )}
                    {gp.example && (
                      <p className="pattern-example">
                        <strong>Ví dụ:</strong> <em>{gp.example}</em>
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Note */}
          {note && (
            <div className="result-section note-section">
              <h3 className="section-title">
                <Info size={16} className="section-icon" />
                Ghi chú ngữ cảnh
              </h3>
              <p className="note-text">{note}</p>
            </div>
          )}
        </div>
      </div>
    );
  }

  return null;
}
