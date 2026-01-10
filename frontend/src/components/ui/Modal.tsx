import React, { useEffect, useRef } from 'react';
import { X, HelpCircle, CheckCircle, Info } from 'lucide-react';

export type ModalType = 'alert' | 'confirm' | 'prompt';

interface ModalProps {
  isOpen: boolean;
  type: ModalType;
  title: string;
  message?: string;
  inputValue?: string;
  placeholder?: string;
  onConfirm: (value?: string) => void;
  onCancel: () => void;
  setInputValue?: (val: string) => void;
}

export const Modal = ({
  isOpen,
  type,
  title,
  message,
  inputValue,
  placeholder,
  onConfirm,
  onCancel,
  setInputValue
}: ModalProps) => {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen && type === 'prompt' && inputRef.current) {
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [isOpen, type]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') onConfirm(inputValue);
    if (e.key === 'Escape') onCancel();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm transition-opacity animate-in fade-in duration-200">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 overflow-hidden transform transition-all scale-100 animate-in zoom-in-95 duration-200 border border-slate-100">
        
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
          <div className="flex items-center gap-3">
            {type === 'alert' && <Info className="text-blue-500" size={24} />}
            {type === 'confirm' && <HelpCircle className="text-orange-500" size={24} />}
            {type === 'prompt' && <CheckCircle className="text-emerald-500" size={24} />}
            <h3 className="font-bold text-slate-800 text-lg">{title}</h3>
          </div>
          <button onClick={onCancel} className="text-slate-400 hover:text-slate-600 transition-colors">
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div className="p-6">
          {message && <p className="text-slate-600 mb-4 leading-relaxed">{message}</p>}
          
          {type === 'prompt' && setInputValue && (
            <div className="mt-2">
              <input
                ref={inputRef}
                type="text"
                className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all"
                value={inputValue}
                placeholder={placeholder}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
              />
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-slate-50 flex justify-end gap-3">
          {type !== 'alert' && (
            <button
              onClick={onCancel}
              className="px-4 py-2 text-slate-600 font-medium hover:bg-slate-200 rounded-lg transition-colors"
            >
              Anuluj
            </button>
          )}
          
          <button
            onClick={() => onConfirm(inputValue)}
            className={`px-6 py-2 text-white font-medium rounded-lg shadow-sm transition-all flex items-center gap-2
              ${type === 'alert' ? 'bg-blue-600 hover:bg-blue-700' : ''}
              ${type === 'confirm' ? 'bg-slate-800 hover:bg-slate-700' : ''}
              ${type === 'prompt' ? 'bg-emerald-600 hover:bg-emerald-700' : ''}
            `}
          >
            {type === 'alert' ? 'Rozumiem' : type === 'prompt' ? 'Zatwierdź' : 'Potwierdź'}
          </button>
        </div>
      </div>
    </div>
  );
};