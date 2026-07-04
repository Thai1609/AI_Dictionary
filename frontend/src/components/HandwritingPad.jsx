import React, { useRef, useState, useEffect } from 'react';
import { Undo2, Trash2, X } from 'lucide-react';

export default function HandwritingPad({ onSelect, onClose }) {
  const canvasRef = useRef(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [strokes, setStrokes] = useState([]);
  const [currentStroke, setCurrentStroke] = useState(null);
  const [results, setResults] = useState([]);

  useEffect(() => {
    drawStrokes();
  }, [strokes, currentStroke]);

  const drawStrokes = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#1d4ed8'; // blue-700

    // Draw previous strokes
    strokes.forEach(stroke => {
      if (stroke.x.length === 0) return;
      ctx.beginPath();
      ctx.moveTo(stroke.x[0], stroke.y[0]);
      for (let i = 1; i < stroke.x.length; i++) {
        ctx.lineTo(stroke.x[i], stroke.y[i]);
      }
      ctx.stroke();
    });

    // Draw current stroke
    if (currentStroke && currentStroke.x.length > 0) {
      ctx.strokeStyle = '#dc2626'; // red-600 for current stroke
      ctx.beginPath();
      ctx.moveTo(currentStroke.x[0], currentStroke.y[0]);
      for (let i = 1; i < currentStroke.x.length; i++) {
        ctx.lineTo(currentStroke.x[i], currentStroke.y[i]);
      }
      ctx.stroke();
    }
  };

  const getCoordinates = (e) => {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    let clientX, clientY;

    if (e.touches && e.touches.length > 0) {
      clientX = e.touches[0].clientX;
      clientY = e.touches[0].clientY;
    } else {
      clientX = e.clientX;
      clientY = e.clientY;
    }

    return {
      x: clientX - rect.left,
      y: clientY - rect.top
    };
  };

  const handlePointerDown = (e) => {
    e.preventDefault(); // Prevent scrolling on touch
    setIsDrawing(true);
    const { x, y } = getCoordinates(e);
    setCurrentStroke({ x: [x], y: [y] });
  };

  const handlePointerMove = (e) => {
    if (!isDrawing || !currentStroke) return;
    e.preventDefault();
    const { x, y } = getCoordinates(e);
    setCurrentStroke(prev => ({
      x: [...prev.x, x],
      y: [...prev.y, y]
    }));
  };

  const handlePointerUp = (e) => {
    if (!isDrawing) return;
    setIsDrawing(false);
    if (currentStroke && currentStroke.x.length > 0) {
      const newStrokes = [...strokes, currentStroke];
      setStrokes(newStrokes);
      setCurrentStroke(null);
      recognize(newStrokes);
    }
  };

  const handlePointerCancel = () => {
    setIsDrawing(false);
    setCurrentStroke(null);
  };

  const recognize = async (currentStrokes) => {
    if (currentStrokes.length === 0) {
      setResults([]);
      return;
    }

    const ink = currentStrokes.map(s => [s.x, s.y]);
    
    const payload = {
      app_version: 0.4,
      api_level: "5.3.rc2",
      device: "5.0",
      input_type: "0",
      options: "enable_pre_space",
      requests: [{
        writing_guide: { writing_area_width: 300, writing_area_height: 300 },
        pre_context: "",
        max_num_results: 10,
        max_completions: 0,
        language: "zh",
        ink: ink
      }]
    };

    try {
      const res = await fetch('https://inputtools.google.com/request?itc=zh-t-i0-handwrit&app=demopage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (data[0] === 'SUCCESS' && data[1] && data[1][0] && data[1][0][1]) {
        setResults(data[1][0][1]);
      }
    } catch (err) {
      console.error("Recognition error:", err);
    }
  };

  const undo = () => {
    if (strokes.length === 0) return;
    const newStrokes = strokes.slice(0, -1);
    setStrokes(newStrokes);
    recognize(newStrokes);
  };

  const clear = () => {
    setStrokes([]);
    setResults([]);
  };

  return (
    <div className="handwriting-pad" style={{
      border: '1px solid var(--color-border)',
      borderRadius: '8px',
      backgroundColor: 'var(--color-bg-card)',
      overflow: 'hidden',
      boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06)',
      display: 'flex',
      flexDirection: 'column',
      width: '100%',
      maxWidth: '400px',
      margin: '0 auto'
    }}>
      {/* Header */}
      <div style={{
        padding: '10px 15px',
        borderBottom: '1px solid var(--color-border)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        backgroundColor: 'var(--color-bg)'
      }}>
        <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 600, color: 'var(--color-text)' }}>Nhận dạng nét vẽ</h4>
        <button type="button" onClick={onClose} style={{
          background: 'transparent',
          border: 'none',
          cursor: 'pointer',
          padding: '4px',
          display: 'flex',
          alignItems: 'center',
          color: 'var(--color-text-light)'
        }}>
          <X size={18} />
        </button>
      </div>

      {/* Results */}
      <div style={{
        padding: '10px',
        borderBottom: '1px solid var(--color-border)',
        minHeight: '44px',
        display: 'flex',
        flexWrap: 'wrap',
        gap: '8px'
      }}>
        {results.map((char, i) => (
          <button
            type="button"
            key={i}
            onClick={() => onSelect(char)}
            style={{
              background: 'transparent',
              border: 'none',
              fontSize: '1.2rem',
              cursor: 'pointer',
              padding: '4px 8px',
              color: 'var(--color-primary)',
              borderRadius: '4px',
              transition: 'background-color 0.2s'
            }}
            onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(0,0,0,0.05)'}
            onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            {char}
          </button>
        ))}
        {results.length === 0 && (
          <span style={{ color: 'var(--color-text-light)', fontSize: '0.9rem', display: 'flex', alignItems: 'center' }}>
            Viết vào khung bên dưới...
          </span>
        )}
      </div>

      {/* Canvas */}
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: '#fafafa',
        touchAction: 'none'
      }}>
        <canvas
          ref={canvasRef}
          width={300}
          height={300}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerOut={handlePointerUp}
          onPointerCancel={handlePointerCancel}
          onTouchStart={handlePointerDown}
          onTouchMove={handlePointerMove}
          onTouchEnd={handlePointerUp}
          onTouchCancel={handlePointerCancel}
          style={{
            touchAction: 'none',
            cursor: 'crosshair',
            background: 'transparent'
          }}
        />
      </div>

      {/* Footer / Toolbar */}
      <div style={{
        padding: '10px 15px',
        borderTop: '1px solid var(--color-border)',
        display: 'flex',
        justifyContent: 'space-between',
        backgroundColor: 'var(--color-bg)'
      }}>
        <button type="button" onClick={undo} disabled={strokes.length === 0} style={{
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          background: 'transparent',
          border: 'none',
          cursor: strokes.length === 0 ? 'not-allowed' : 'pointer',
          color: strokes.length === 0 ? 'var(--color-text-lighter)' : 'var(--color-text)',
          fontSize: '0.9rem',
          padding: '6px 12px',
          borderRadius: '4px'
        }}>
          <Undo2 size={16} /> Hoàn tác
        </button>
        <button type="button" onClick={clear} disabled={strokes.length === 0} style={{
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          background: 'transparent',
          border: 'none',
          cursor: strokes.length === 0 ? 'not-allowed' : 'pointer',
          color: strokes.length === 0 ? 'var(--color-text-lighter)' : '#dc2626',
          fontSize: '0.9rem',
          padding: '6px 12px',
          borderRadius: '4px'
        }}>
          <Trash2 size={16} /> Xóa tất cả
        </button>
      </div>
    </div>
  );
}
