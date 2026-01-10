import { useState, useEffect } from 'react';
import { 
  Activity, 
  HardDrive, 
  Clock, 
  Server,
  Microchip
} from 'lucide-react';
import { systemService } from '../services/api';
import type { SystemStats } from '../types/api';

const MiniChart = ({ data, color, maxVal = 100, suffix = '' }: { data: number[], color: string, maxVal?: number, suffix?: string }) => {
  const height = 60;
  const width = 100;
  
  const points = data.map((val, i) => {
    const x = (i / (data.length - 1)) * width;
    const y = height - (val / maxVal) * height;
    return `${x},${y}`;
  }).join(' ');

  return (
    <div className="relative h-24 w-full bg-slate-50 rounded-lg border border-slate-200 overflow-hidden mt-2">
      <div className="absolute inset-0 grid grid-cols-4 grid-rows-2">
        <div className="border-r border-slate-200/50 border-b"></div>
        <div className="border-r border-slate-200/50 border-b"></div>
        <div className="border-r border-slate-200/50 border-b"></div>
        <div className="border-b border-slate-200/50"></div>
        <div className="border-r border-slate-200/50"></div>
        <div className="border-r border-slate-200/50"></div>
        <div className="border-r border-slate-200/50"></div>
      </div>

      <svg viewBox={`0 0 ${width} ${height}`} className="absolute inset-0 w-full h-full p-2" preserveAspectRatio="none">
        <polyline 
          fill="none" 
          stroke={color} 
          strokeWidth="2" 
          points={points} 
          vectorEffect="non-scaling-stroke"
          strokeLinejoin="round"
        />
      </svg>
      
      <div className="absolute top-1 right-2 text-xs font-bold text-slate-500 bg-white/80 px-1 rounded">
        {data[data.length - 1]?.toFixed(1)}{suffix}
      </div>
    </div>
  );
};

export const System = () => {
  const [stats, setStats] = useState<SystemStats | null>(null);
  const [cpuHistory, setCpuHistory] = useState<number[]>(new Array(30).fill(0));
  const [ramHistory, setRamHistory] = useState<number[]>(new Array(30).fill(0));
  
  const [uptime, setUptime] = useState<string>('-');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const data = await systemService.getStats();
        setStats(data);
        setUptime(data.systemUptime);

        setCpuHistory(prev => {
          const newHist = [...prev.slice(1), data.cpuTemperature];
          return newHist;
        });

        setRamHistory(prev => {
          const percent = (data.usedMemoryMB / data.totalMemoryMB) * 100;
          const newHist = [...prev.slice(1), percent];
          return newHist;
        });

      } catch (error) {
        console.error("Błąd pobierania statystyk", error);
      }
    };

    fetchData();

    const interval = setInterval(fetchData, 2000);
    return () => clearInterval(interval);
  }, []);

  const formatBytes = (mb: number) => {
    if (mb > 1024 * 1024) return `${(mb / (1024 * 1024)).toFixed(2)} TB`;
    if (mb > 1024) return `${(mb / 1024).toFixed(2)} GB`;
    return `${mb} MB`;
  };

  if (!stats) return <div className="p-8 text-center text-slate-500 animate-pulse">Inicjalizacja monitora wydajności...</div>;

  return (
    <div className="space-y-6 max-w-6xl mx-auto">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Wydajność Systemu</h1>
          <p className="text-slate-500">Monitorowanie zasobów w czasie rzeczywistym.</p>
        </div>
        <div className="bg-white px-4 py-2 rounded-lg border border-slate-200 flex items-center gap-2 text-sm text-slate-600 shadow-sm">
          <Clock size={16} className="text-blue-500" />
          <span className="font-mono">{uptime}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        
        <div className="space-y-6">
          
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-orange-50 text-orange-600 rounded-lg">
                  <Microchip size={24} />
                </div>
                <div>
                  <h3 className="font-bold text-slate-700">Procesor (Temperatura)</h3>
                </div>
              </div>
              <div className="text-right">
                <h2 className="text-2xl font-bold text-slate-800">{stats.cpuTemperature.toFixed(1)}°C</h2>
              </div>
            </div>
            <MiniChart data={cpuHistory} color="#f97316" maxVal={100} suffix="°C" />
            <div className="mt-3 grid grid-cols-2 gap-4 text-xs text-slate-500">
            </div>
          </div>

          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
                  <Activity size={24} />
                </div>
                <div>
                  <h3 className="font-bold text-slate-700">Pamięć RAM</h3>
                </div>
              </div>
              <div className="text-right">
                <h2 className="text-2xl font-bold text-slate-800">
                  {((stats.usedMemoryMB / stats.totalMemoryMB) * 100).toFixed(0)}%
                </h2>
              </div>
            </div>
            <MiniChart data={ramHistory} color="#9333ea" maxVal={100} suffix="%" />
            <div className="mt-3 flex justify-between text-sm text-slate-600 font-mono border-t border-slate-100 pt-2">
               <span>Użyte: {formatBytes(stats.usedMemoryMB)}</span>
               <span>Całkowite: {formatBytes(stats.totalMemoryMB)}</span>
            </div>
          </div>

        </div>

        <div className="space-y-6">
          
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm h-full">
            <div className="flex items-center gap-3 mb-6">
              <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
                <HardDrive size={24} />
              </div>
              <div>
                <h3 className="font-bold text-slate-700">Pamięć Masowa</h3>
                <p className="text-xs text-slate-400">Zmontowane partycje i dyski</p>
              </div>
            </div>

            <div className="space-y-6">
              {stats.disks.map((disk, idx) => {
                const usedSpace = disk.totalSpaceMB - disk.usableSpaceMB;
                const percentage = (usedSpace / disk.totalSpaceMB) * 100;
                
                return (
                  <div key={idx} className="group">
                    <div className="flex justify-between items-end mb-1">
                      <div className="flex items-center gap-2">
                        <Server size={16} className="text-slate-400" />
                        <span className="font-medium text-slate-700 text-sm">{disk.path}</span>
                      </div>
                      <span className="text-xs font-mono text-slate-500">
                        {percentage.toFixed(1)}%
                      </span>
                    </div>
                    
                    <div className="h-3 w-full bg-slate-100 rounded-full overflow-hidden">
                      <div 
                        className={`h-full rounded-full transition-all duration-1000 ${
                          percentage > 90 ? 'bg-red-500' : percentage > 70 ? 'bg-amber-500' : 'bg-emerald-500'
                        }`}
                        style={{ width: `${percentage}%` }}
                      />
                    </div>
                    
                    <div className="flex justify-between mt-1 text-xs text-slate-400">
                      <span>Wolne: {formatBytes(disk.usableSpaceMB)}</span>
                      <span>Razem: {formatBytes(disk.totalSpaceMB)}</span>
                    </div>
                  </div>
                );
              })}
            </div>
            
            <div className="mt-8 pt-4 border-t border-slate-100">
               <h4 className="text-sm font-semibold text-slate-700 mb-2">Podsumowanie przestrzeni</h4>
               <div className="grid grid-cols-2 gap-4">
                  <div className="p-3 bg-slate-50 rounded-lg text-center">
                    <div className="text-xs text-slate-500 uppercase tracking-wide">Całkowita</div>
                    <div className="text-lg font-bold text-slate-800">{formatBytes(stats.totalSpaceMB)}</div>
                  </div>
                  <div className="p-3 bg-slate-50 rounded-lg text-center">
                    <div className="text-xs text-slate-500 uppercase tracking-wide">Dostępna</div>
                    <div className="text-lg font-bold text-emerald-600">{formatBytes(stats.totalUsableSpaceMB)}</div>
                  </div>
               </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
};