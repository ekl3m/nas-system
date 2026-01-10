import { useState, useEffect } from 'react';
import { 
  ShieldAlert, 
  AlertTriangle, 
  Info, 
  CheckCircle, 
  RefreshCw, 
  Server,
  UserCheck,
  LogOut,
  Activity,
  Cpu
} from 'lucide-react';
import { systemService } from '../services/api';

type LogTab = 'events' | 'system';

export const Logs = () => {
  const [activeTab, setActiveTab] = useState<LogTab>('events');
  const [logs, setLogs] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchLogs();
  }, [activeTab]);

  const fetchLogs = async () => {
    setLoading(true);
    setLogs([]); 
    try {
      let data: string[] = [];
      
      
      if (activeTab === 'events') {
        data = await systemService.getEventLogs();
      } else {
        data = await systemService.getSystemLogs();
      }

      setLogs(data.reverse());
    } catch (error) {
      console.error("Błąd pobierania logów", error);
    } finally {
      setLoading(false);
    }
  };

  const getLogCategory = (text: string) => {
    const lower = text.toLowerCase();
    
    if (lower.includes('error') || lower.includes('failed') || lower.includes('exception') || lower.includes('critical')) return 'error';
    if (lower.includes('warn')) return 'warning';
    
    if (lower.includes('success') || lower.includes('started') || lower.includes('ready')) return 'success';
    
    if (lower.includes('login') || lower.includes('auth')) return 'auth';
    if (lower.includes('logout')) return 'logout';

    if (lower.includes('kernel') || lower.includes('driver') || lower.includes('boot')) return 'system';
    
    return 'info';
  };

  const getStyle = (category: string) => {
    switch (category) {
      case 'error': return { 
        bg: 'bg-red-50', border: 'border-red-100', 
        icon: <ShieldAlert className="text-red-600" size={18} /> 
      };
      case 'warning': return { 
        bg: 'bg-amber-50', border: 'border-amber-100', 
        icon: <AlertTriangle className="text-amber-600" size={18} /> 
      };
      case 'success': return { 
        bg: 'bg-emerald-50', border: 'border-emerald-100', 
        icon: <CheckCircle className="text-emerald-600" size={18} /> 
      };
      case 'auth': return { 
        bg: 'bg-violet-50', border: 'border-violet-100', 
        icon: <UserCheck className="text-violet-600" size={18} /> 
      };
      case 'system': return { 
        bg: 'bg-slate-100', border: 'border-slate-200', 
        icon: <Cpu className="text-slate-600" size={18} /> 
      };
      case 'logout': return { 
        bg: 'bg-slate-50', border: 'border-slate-200', 
        icon: <LogOut className="text-slate-500" size={18} /> 
      };
      default: return { 
        bg: 'bg-blue-50', border: 'border-blue-100', 
        icon: <Info className="text-blue-600" size={18} /> 
      };
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Dziennik Systemowy</h1>
          <p className="text-slate-500">Przegląd zdarzeń aplikacji oraz logów systemowych.</p>
        </div>
        
        <button 
          onClick={fetchLogs}
          className="flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 text-slate-600 rounded-lg hover:bg-slate-50 transition-colors shadow-sm text-sm font-medium w-fit"
        >
          <RefreshCw size={16} className={loading ? "animate-spin" : ""} />
          Odśwież
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden min-h-[500px] flex flex-col">
        
        <div className="flex border-b border-slate-100">
          <button
            onClick={() => setActiveTab('events')}
            className={`flex-1 py-4 text-sm font-medium flex items-center justify-center gap-2 transition-colors relative ${
              activeTab === 'events' 
                ? 'text-blue-600 bg-blue-50/50' 
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}
          >
            <ShieldAlert size={18} />
            Event Logs
            {activeTab === 'events' && (
              <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-600" />
            )}
          </button>
          
          <button
            onClick={() => setActiveTab('system')}
            className={`flex-1 py-4 text-sm font-medium flex items-center justify-center gap-2 transition-colors relative ${
              activeTab === 'system' 
                ? 'text-blue-600 bg-blue-50/50' 
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}
          >
            <Activity size={18} />
            System Logs
            {activeTab === 'system' && (
              <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-600" />
            )}
          </button>
        </div>

        <div className="flex-1">
          {loading ? (
            <div className="h-full flex items-center justify-center text-slate-400 gap-2">
              <RefreshCw className="animate-spin" size={20} /> Wczytywanie danych...
            </div>
          ) : logs.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center p-12 text-center">
              <div className="bg-slate-50 w-16 h-16 rounded-full flex items-center justify-center mb-3">
                <Server className="text-slate-300" size={32} />
              </div>
              <p className="text-slate-500 font-medium">Brak wpisów w tej kategorii.</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {logs.map((log, index) => {
                const category = getLogCategory(log);
                const style = getStyle(category);
                
                return (
                  <div key={index} className="p-4 hover:bg-slate-50 transition-colors flex items-start gap-4">
                    <div className={`mt-0.5 min-w-[36px] h-9 rounded-lg flex items-center justify-center border ${style.bg} ${style.border}`}>
                      {style.icon}
                    </div>
                    
                    <div className="flex-1 min-w-0">
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
    </div>
  );
};