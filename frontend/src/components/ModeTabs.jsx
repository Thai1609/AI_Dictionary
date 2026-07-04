import React from 'react';
import { BookOpen, HelpCircle, FileCheck, Search } from 'lucide-react';

export default function ModeTabs({ activeMode, onChangeMode, disabled }) {
  const modes = [
    { id: 'search', label: 'Tìm kiếm', icon: Search },
    { id: 'word', label: 'Tra từ', icon: BookOpen },
    { id: 'sentence', label: 'Phân tích câu', icon: HelpCircle },
  ];

  return (
    <div className="mode-tabs" id="mode-tabs">
      {modes.map((m) => {
        const Icon = m.icon;
        return (
          <button
            key={m.id}
            id={`tab-${m.id}`}
            type="button"
            className={`mode-tab-btn ${activeMode === m.id ? 'active' : ''}`}
            onClick={() => !disabled && onChangeMode(m.id)}
            disabled={disabled}
          >
            <Icon className="tab-icon" size={18} />
            <span>{m.label}</span>
          </button>
        );
      })}
    </div>
  );
}
