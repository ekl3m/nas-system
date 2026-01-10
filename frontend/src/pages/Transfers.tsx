import { useState, useEffect } from 'react';
import { 
  ArrowRightLeft, 
  Upload, 
  Download, 
  Trash2, 
  FileText, 
  RefreshCw,
  Move
} from 'lucide-react';
import { systemService } from '../services/api';

export const Transfers = () => {
  const [logs, setLogs] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchLogs = async () => {
    setLoading(true);
    try {
      const data = await systemService.getTransferLogs();
      setLogs(data.reverse());
    } catch (error) {
      console.error("Błąd pobierania historii transferów", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  const getLogType = (text: string) => {
    const lower = text.toLowerCase();
    if (lower.includes('upload') || lower.includes('uploaded')) return 'upload';
    if (lower.includes('download')) return 'download';
    if (lower.includes('delete') || lower.includes('remove')) return 'delete';
    if (lower.includes('move') || lower.includes('rename')) return 'move';
    return 'info';
  };

  const renderIcon = (type: string) => {
    switch (type) {
      case 'upload': return <Upload size={18} className="text-blue-600" />;
      case 'download': return <Download size={18} className="text-emerald-600" />;
      case 'delete': return <Trash2 size={18} className="text-red-600" />;
      case 'move': return <Move size={18} className="text-orange-600" />;
      default: return <FileText size={18} className="text-slate-400" />;
    }
  };

  const getBgColor = (type: string) => {
    switch (type) {
      case 'upload': return 'bg-blue-50 border-blue-100';
      case 'download': return 'bg-emerald-50 border-emerald-100';
      case 'delete': return 'bg-red-50 border-red-100';
      case 'move': return 'bg-orange-50 border-orange-100';
      default: return 'bg-slate-50 border-slate-100';
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Historia Transferów</h1>
          <p className="text-slate-500">Rejestr operacji na plikach (Upload, Download, Delete).</p>
        </div>
        <button 
          onClick={fetchLogs}
          className="flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 text-slate-600 rounded-lg hover:bg-slate-50 transition-colors shadow-sm text-sm font-medium"
        >
          <RefreshCw size={16} className={loading ? "animate-spin" : ""} />
          Odśwież
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-slate-400">
            Ładowanie historii...
          </div>
        ) : logs.length === 0 ? (
          <div className="p-12 text-center">
            <div className="bg-slate-50 w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-3">
              <ArrowRightLeft className="text-slate-300" size={32} />
            </div>
            <p className="text-slate-500 font-medium">Brak zarejestrowanych transferów.</p>
            <p className="text-xs text-slate-400 mt-1">Wykonaj operacje na plikach, aby zobaczyć je tutaj.</p>
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {logs.map((log, index) => {
              const type = getLogType(log);
              return (
                <div key={index} className="p-4 hover:bg-slate-50 transition-colors flex items-start gap-4">
                  <div className={`mt-1 min-w-[36px] h-9 rounded-lg flex items-center justify-center border ${getBgColor(type)}`}>
                    {renderIcon(type)}
                  </div>
                  
                  <div className="flex-1">
                    <p className="text-sm text-slate-700 font-mono leading-relaxed break-all">
                      {log}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};