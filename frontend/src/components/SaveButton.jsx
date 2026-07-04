import React, { useState, useEffect } from 'react';
import { saveWord } from '../api/dictionaryApi';
import { Bookmark, BookmarkCheck, AlertTriangle, Loader2 } from 'lucide-react';

export default function SaveButton({
  type,
  sourceLanguage,
  targetLanguage,
  searchKeyword,
  dictionary
}) {
  const [status, setStatus] = useState('idle'); // 'idle' | 'saving' | 'success' | 'exists' | 'failed'
  const [msg, setMsg] = useState('');

  // Reset status if the dictionary word changes
  useEffect(() => {
    setStatus('idle');
    setMsg('');
  }, [dictionary]);

  const handleSave = async () => {
    if (status === 'saving' || status === 'success' || status === 'exists') return;
    setStatus('saving');
    setMsg('');

    try {
      const response = await saveWord({
        type: type || 'word',
        sourceLanguage,
        targetLanguage,
        searchKeyword: searchKeyword?.trim(),
        dictionary
      });

      if (response && (response.success || response.id)) {
        setStatus('success');
        setMsg(response.message || 'Lưu thành công');
      } else {
        const message = response?.message || '';
        if (message.includes('tồn tại') || message.includes('exist') || message.includes('đã có')) {
          setStatus('exists');
          setMsg('Từ đã tồn tại trong database');
        } else {
          setStatus('failed');
          setMsg(message || 'Lưu thất bại');
        }
      }
    } catch (err) {
      console.error('Error saving to DB:', err);
      const errMsg = err.message || '';
      if (errMsg.includes('tồn tại') || errMsg.includes('exist') || errMsg.includes('đã có')) {
        setStatus('exists');
        setMsg('Từ đã tồn tại');
      } else {
        setStatus('failed');
        setMsg(errMsg || 'Lưu thất bại');
      }
    }
  };

  return (
    <div className="save-button-section" id="save-button-container">
      <button
        type="button"
        id="btn-save-word"
        className={`save-action-btn btn-state-${status}`}
        onClick={handleSave}
        disabled={status === 'saving'}
      >
        {status === 'saving' && (
          <>
            <Loader2 className="animate-spin mr-2" size={16} />
            <span>Đang lưu...</span>
          </>
        )}
        {status === 'idle' && (
          <>
            <Bookmark className="mr-2" size={16} />
            <span>Thêm vào DB</span>
          </>
        )}
        {status === 'success' && (
          <>
            <BookmarkCheck className="mr-2" size={16} />
            <span>Lưu thành công</span>
          </>
        )}
        {status === 'exists' && (
          <>
            <BookmarkCheck className="mr-2" size={16} />
            <span>Từ đã tồn tại</span>
          </>
        )}
        {status === 'failed' && (
          <>
            <AlertTriangle className="mr-2" size={16} />
            <span>Lưu thất bại</span>
          </>
        )}
      </button>
      {msg && <span className={`save-status-msg msg-${status}`}>{msg}</span>}
    </div>
  );
}
