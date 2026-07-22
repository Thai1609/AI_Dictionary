import React, { useState, useRef, useEffect } from 'react';
import { Search, Loader2, PenTool, Image as ImageIcon } from 'lucide-react';
import HandwritingPad from './HandwritingPad';
import { recognizeImage } from '../api/dictionaryApi';

export default function SearchBox({
  text,
  setText,
  onSubmit,
  loading,
  mode
}) {
  const [showPad, setShowPad] = useState(false);
  const [imageLoading, setImageLoading] = useState(false);
  const wrapperRef = useRef(null);
  const fileInputRef = useRef(null);

  const getPlaceholder = () => {
    switch (mode) {
      case 'search':
        return 'Nhập từ khóa cần tìm kiếm...';
      case 'word':
        return 'Nhập từ/cụm từ cần tra cứu ...';
      case 'sentence':
        return 'Nhập câu tiếng Trung cần dịch và phân tích...';
      default:
        return 'Nhập nội dung cần tra cứu...';
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!text.trim()) return;
    onSubmit();
  };

  const handleKeyDown = (e) => {
    // Submit on Enter without shift key
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (text.trim() && !loading && !imageLoading) {
        onSubmit();
      }
    }
  };

  const handleSelectChar = (char) => {
    setText((prev) => prev + char);
  };

  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      alert('Vui lòng chọn một tệp hình ảnh.');
      return;
    }

    try {
      setImageLoading(true);
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = async () => {
        try {
          const base64Image = reader.result;
          const extractedText = await recognizeImage(base64Image);
          if (extractedText) {
            setText(extractedText.trim());
          }
        } catch (err) {
          alert(err.message || 'Lỗi khi nhận dạng hình ảnh.');
        } finally {
          setImageLoading(false);
          if (fileInputRef.current) {
            fileInputRef.current.value = '';
          }
        }
      };
      reader.onerror = () => {
        alert('Lỗi đọc tệp hình ảnh.');
        setImageLoading(false);
      };
    } catch (err) {
      console.error(err);
      setImageLoading(false);
    }
  };

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target)) {
        setShowPad(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('touchstart', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('touchstart', handleClickOutside);
    };
  }, []);

  return (
    <div className="search-box-wrapper" id="search-box" ref={wrapperRef} style={{ position: 'relative' }}>
      <form onSubmit={handleSubmit} className="search-form">
        <div className="textarea-container" style={{ position: 'relative' }}>
          <textarea
            id="search-input"
            className="search-input"
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={getPlaceholder()}
            disabled={loading}
            rows={mode === 'word' || mode === 'search' ? 2 : 3}
          />
          
          <div style={{ position: 'absolute', bottom: '8px', right: '8px', display: 'flex', gap: '8px' }}>
            <input
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              ref={fileInputRef}
              onChange={handleImageUpload}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={imageLoading || loading}
              style={{
                background: 'transparent',
                color: 'var(--color-text-light)',
                border: 'none',
                cursor: (imageLoading || loading) ? 'not-allowed' : 'pointer',
                padding: '6px',
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.2s',
                opacity: (imageLoading || loading) ? 0.5 : 1
              }}
              title="Dịch bằng hình ảnh"
            >
              {imageLoading ? <Loader2 size={18} className="animate-spin" /> : <ImageIcon size={18} />}
            </button>
            <button
              type="button"
              onClick={() => setShowPad(!showPad)}
              style={{
                background: showPad ? 'var(--color-primary-light)' : 'transparent',
                color: showPad ? 'var(--color-primary)' : 'var(--color-text-light)',
                border: 'none',
                cursor: 'pointer',
                padding: '6px',
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.2s'
              }}
              title="Nhận dạng nét vẽ"
            >
              <PenTool size={18} />
            </button>
            {text && !loading && (
              <button
                type="button"
                onClick={() => setText('')}
                style={{
                  background: 'transparent',
                  color: 'var(--color-text-light)',
                  border: 'none',
                  cursor: 'pointer',
                  padding: '6px',
                  borderRadius: '6px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '18px',
                  lineHeight: 1
                }}
                title="Xóa văn bản"
              >
                &times;
              </button>
            )}
          </div>
          
          {showPad && (
            <div style={{
              position: 'absolute',
              top: '100%',
              left: 0,
              right: 0,
              zIndex: 50,
              marginTop: '4px'
            }}>
              <HandwritingPad onSelect={handleSelectChar} onClose={() => setShowPad(false)} />
            </div>
          )}
        </div>

        <div className="search-btn-container">
          <button
            type="submit"
            id="search-submit-btn"
            className="search-submit-btn"
            disabled={loading || !text.trim()}
          >
            {loading ? (
              <>
                <Loader2 className="animate-spin mr-2" size={18} />
                <span>{mode === 'search' ? 'Đang tìm...' : 'Đang phân tích...'}</span>
              </>
            ) : (
              <>
                <Search size={18} className="mr-2" />
                <span>{mode === 'search' ? 'Tìm kiếm' : 'Tra cứu'}</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
