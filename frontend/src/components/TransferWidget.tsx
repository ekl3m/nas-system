import { useTransfer } from '../context/TransferContext';
import { X, Minimize2, CheckCircle, AlertCircle, Loader, UploadCloud } from 'lucide-react';

export const TransferWidget = () => {
  const { transfers, isWidgetOpen, toggleWidget, clearCompleted } = useTransfer();

  if (transfers.length === 0) return null;

  const activeCount = transfers.filter(t => t.status === 'uploading' || t.status === 'pending').length;
  const completedCount = transfers.length - activeCount;

  if (!isWidgetOpen) {
    return (
      <button 
        onClick={toggleWidget}
        className="fixed bottom-6 right-6 z-50 bg-blue-600 text-white p-4 rounded-full shadow-lg hover:bg-blue-700 transition-all flex items-center justify-center gap-2 animate-in slide-in-from-bottom-4"
      >
        {activeCount > 0 ? <Loader className="animate-spin" size={20} /> : <CheckCircle size={20} />}
        <span className="font-bold">{activeCount > 0 ? activeCount : completedCount}</span>
      </button>
    );
  }

  return (
    <div className="fixed bottom-6 right-6 z-50 w-80 bg-white rounded-xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col animate-in slide-in-from-bottom-4 fade-in duration-200">
      <div className="bg-slate-900 text-white p-3 flex justify-between items-center">
        <div className="flex items-center gap-2">
            <UploadCloud size={18} />
            <span className="font-medium text-sm">Transfery ({activeCount})</span>
        </div>
        <div className="flex gap-1">
            <button onClick={toggleWidget} className="p-1 hover:bg-white/20 rounded"><Minimize2 size={16} /></button>
            <button onClick={clearCompleted} className="p-1 hover:bg-white/20 rounded" title="Wyczyść zakończone"><X size={16} /></button>
        </div>
      </div>

      <div className="max-h-64 overflow-y-auto p-2 space-y-2 bg-slate-50">
        {transfers.map((item) => (
          <div key={item.id} className="bg-white p-3 rounded-lg border border-slate-100 shadow-sm">
            <div className="flex justify-between items-start mb-1">
                <span className="text-xs font-medium text-slate-700 truncate max-w-[200px]" title={item.name}>{item.name}</span>
                {item.status === 'completed' && <CheckCircle size={14} className="text-emerald-500" />}
                {item.status === 'error' && <AlertCircle size={14} className="text-red-500" />}
                {item.status === 'uploading' && <span className="text-xs font-mono text-blue-600">{item.progress}%</span>}
            </div>
            
            <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                <div 
                    className={`h-full transition-all duration-300 ${
                        item.status === 'completed' ? 'bg-emerald-500' : 
                        item.status === 'error' ? 'bg-red-500' : 
                        'bg-blue-500'
                    }`} 
                    style={{ width: `${item.progress}%` }}
                />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};