import React, { useEffect, useState, useRef } from 'react';
import { 
  HardDrive, Upload, FolderPlus, Clock, ArrowRight, Activity, Cpu, CheckCircle, 
  AlertTriangle, Folder, FileText, FileImage, FileVideo, Music, FileCode, RefreshCw 
} from 'lucide-react';
import { fileService, systemService } from '../services/api';
import type { FileInfo, SystemStats } from '../types/api';
import { Card } from '../components/ui/Card';
import { useAuth } from '../context/AuthContext';
import { useModal } from '../context/ModalContext';

export const Dashboard = () => {
  const { user } = useAuth();
  const modal = useModal();
  const [recentFiles, setRecentFiles] = useState<FileInfo[]>([]);
  const [stats, setStats] = useState<SystemStats | null>(null);
  const [loading, setLoading] = useState(true);
  
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const loadDashboardData = async () => {
      try {
        const recent = await fileService.getRecent(5);
        setRecentFiles(recent);
        const sysStats = await systemService.getStats();
        setStats(sysStats);
      } catch (error) {
        console.error("Błąd ładowania dashboardu:", error);
      } finally {
        setLoading(false);
      }
    };
    loadDashboardData();
  }, []);

  const handleQuickUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files?.length) return;
    const file = e.target.files[0];

    try {
      await fileService.upload("", file);
      await modal.showAlert("Sukces", "Plik został wgrany do folderu głównego.");
      
      const recent = await fileService.getRecent(5);
      setRecentFiles(recent);
    } catch (error) {
      await modal.showAlert("Błąd", "Wystąpił błąd podczas wgrywania pliku.");
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleQuickFolder = async () => {
    const name = await modal.showPrompt("Nowy Folder", "Podaj nazwę folderu (zostanie utworzony w głównym katalogu):");
    if (!name) return;
    
    try {
      await fileService.createFolder(name);
      await modal.showAlert("Sukces", "Folder został utworzony.");
    } catch (error) {
      await modal.showAlert("Błąd", "Nie udało się utworzyć folderu.");
    }
  };

  const handleSystemRestart = async () => {
    const confirmed = await modal.showConfirm(
      "Restart Systemu", 
      "Czy na pewno chcesz zrestartować serwer? Usługi będą chwilowo niedostępne."
    );
    if (!confirmed) return;

    try {
      await systemService.reboot();
      await modal.showAlert("Info", "Polecenie restartu zostało wysłane.");
    } catch (error) {
      await modal.showAlert("Błąd", "Wystąpił problem z wysłaniem polecenia restartu.");
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const day = date.getDate().toString().padStart(2, '0');
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const year = date.getFullYear();
    return `${day}/${month}/${year}`;
  };

  const getIcon = (file: FileInfo) => {
    if (file.isDirectory) return <Folder className="text-blue-500 fill-blue-50" size={20} />;
    const ext = file.name.split('.').pop()?.toLowerCase() || '';
    const size = 20;
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return <FileImage className="text-purple-500" size={size} />;
    if (['mp4', 'mkv', 'mov', 'avi', 'webm'].includes(ext)) return <FileVideo className="text-red-500" size={size} />;
    if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return <Music className="text-pink-500" size={size} />;
    if (['js', 'ts', 'jsx', 'tsx', 'java', 'py', 'html', 'css', 'json'].includes(ext)) return <FileCode className="text-emerald-500" size={size} />;
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return <HardDrive className="text-orange-500" size={size} />;
    return <FileText className="text-slate-400" size={size} />;
  };

  if (loading) return <div className="p-8 text-center text-slate-500 animate-pulse">Ładowanie pulpitu...</div>;

  const totalSpace = stats?.totalSpaceMB || 0;
  const freeSpace = stats?.totalUsableSpaceMB || 0;
  const usedSpace = totalSpace - freeSpace;
  const storagePercent = totalSpace > 0 ? Math.round((usedSpace / totalSpace) * 100) : 0;
  const totalRam = stats?.totalMemoryMB || 0;
  const usedRam = stats?.usedMemoryMB || 0;
  const ramPercent = totalRam > 0 ? Math.round((usedRam / totalRam) * 100) : 0;
  const cpuTemp = stats?.cpuTemperature?.toFixed(1) || '-';
  const uptime = stats?.systemUptime || '-';

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Witaj, {user?.username}!</h1>
          <p className="text-slate-500">Przegląd statusu serwera NAS.</p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1 bg-emerald-50 text-emerald-700 rounded-full text-sm font-medium border border-emerald-100">
          <CheckCircle size={16} /> System Online
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-start mb-2">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Pamięć Masowa</p>
              <h3 className="text-2xl font-bold text-slate-800 mt-1">{storagePercent}%</h3>
            </div>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <HardDrive size={20} />
            </div>
          </div>
          <div className="w-full bg-slate-100 rounded-full h-1.5 mb-2">
            <div className="bg-blue-500 h-1.5 rounded-full" style={{ width: `${storagePercent}%` }}></div>
          </div>
          <p className="text-xs text-slate-400">Wolne: {formatBytes(freeSpace)} / {formatBytes(totalSpace)}</p>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-start mb-2">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Pamięć RAM</p>
              <h3 className="text-2xl font-bold text-slate-800 mt-1">{ramPercent}%</h3>
            </div>
            <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
              <Activity size={20} />
            </div>
          </div>
          <div className="w-full bg-slate-100 rounded-full h-1.5 mb-2">
            <div className="bg-purple-500 h-1.5 rounded-full" style={{ width: `${ramPercent}%` }}></div>
          </div>
          <p className="text-xs text-slate-400">Użycie: {usedRam} MB / {totalRam} MB</p>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-start mb-2">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Temperatura CPU</p>
              <h3 className="text-2xl font-bold text-slate-800 mt-1">{cpuTemp}°C</h3>
            </div>
            <div className="p-2 bg-orange-50 text-orange-600 rounded-lg">
              <Cpu size={20} />
            </div>
          </div>
          <div className="h-4"></div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-start mb-2">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Czas Pracy</p>
              <h3 className="text-lg font-bold text-slate-800 mt-1 truncate" title={uptime}>{uptime}</h3>
            </div>
            <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
              <Clock size={20} />
            </div>
          </div>
          <div className="h-4"></div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1 space-y-6">
            <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm">
                <h3 className="font-bold text-slate-700 mb-4 flex items-center gap-2">
                    <Activity size={20} className="text-orange-500"/>
                    Szybkie Akcje
                </h3>
                <div className="grid grid-cols-2 gap-3">
                    <input type="file" ref={fileInputRef} onChange={handleQuickUpload} className="hidden" />
                    <button onClick={() => fileInputRef.current?.click()} className="flex flex-col items-center justify-center gap-2 p-4 border border-slate-200 border-dashed rounded-lg hover:bg-blue-50 hover:border-blue-300 hover:text-blue-600 transition-all text-slate-600 bg-slate-50/50">
                        <Upload size={24} /><span className="text-xs font-bold">Dodaj Plik</span>
                    </button>
                    <button onClick={handleQuickFolder} className="flex flex-col items-center justify-center gap-2 p-4 border border-slate-200 border-dashed rounded-lg hover:bg-orange-50 hover:border-orange-300 hover:text-orange-600 transition-all text-slate-600 bg-slate-50/50">
                        <FolderPlus size={24} /><span className="text-xs font-bold">Nowy Folder</span>
                    </button>
                    <button onClick={handleSystemRestart} className="col-span-2 flex items-center justify-center gap-2 p-3 border border-slate-200 rounded-lg hover:bg-red-50 hover:border-red-300 hover:text-red-600 transition-all text-slate-600 mt-2">
                        <RefreshCw size={20} /><span className="text-xs font-bold">Restart Systemu</span>
                    </button>
                </div>
                <div className="mt-4 p-3 bg-blue-50 rounded-lg flex gap-3 items-start">
                    <AlertTriangle size={16} className="text-blue-600 shrink-0 mt-0.5" />
                    <p className="text-xs text-blue-800">Pliki dodane tutaj trafią bezpośrednio do katalogu głównego Twojego konta.</p>
                </div>
            </div>
        </div>

        <div className="lg:col-span-2">
          <Card title="Ostatnio dodane pliki">
            {recentFiles.length === 0 ? (
                <div className="p-12 text-center text-slate-400 italic">Brak ostatnich plików.</div>
            ) : (
                <div className="divide-y divide-slate-100">
                    {recentFiles.map((file, idx) => (
                        <div key={idx} className="flex items-center justify-between p-3 hover:bg-slate-50 rounded-lg transition-colors group">
                            <div className="flex items-center gap-3 overflow-hidden">
                                <div className="p-2 bg-slate-100 rounded group-hover:bg-white transition-colors shrink-0">
                                    {getIcon(file)}
                                </div>
                                <div className="min-w-0">
                                    <p className="text-sm font-medium text-slate-700 truncate">{file.name}</p>
                                    <p className="text-xs text-slate-400">{formatDate(file.lastModified)}</p>
                                </div>
                            </div>
                            <div className="flex items-center gap-4 shrink-0">
                                <span className="text-xs font-mono text-slate-400 hidden sm:inline">{formatBytes(file.size)}</span>
                                <div className="p-1 text-slate-300"><ArrowRight size={16} /></div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};