import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Power, RefreshCw, Save, ShieldCheck, AlertCircle, User, LogOut } from 'lucide-react';
import { systemService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/ui/Card';
import { useModal } from '../context/ModalContext';

export const Settings = () => {
  const { user, logout } = useAuth();
  const modal = useModal();
  const navigate = useNavigate();
  const [backupLoading, setBackupLoading] = useState(false);

  const handleLogout = async () => {
    const confirmed = await modal.showConfirm("Wylogowanie", "Czy na pewno chcesz się wylogować?");
    if (confirmed) {
      logout();
      navigate('/login');
    }
  };

  const handlePowerAction = async (action: 'reboot' | 'shutdown') => {
    const msg = action === 'reboot' 
      ? 'Czy na pewno chcesz zrestartować system?' 
      : 'Czy na pewno chcesz wyłączyć serwer? Wymagane będzie fizyczne włączenie.';
      
    const confirmed = await modal.showConfirm(
        action === 'reboot' ? 'Restart Systemu' : 'Wyłączenie Serwera', 
        msg
    );
    if (!confirmed) return;

    try {
      if (action === 'reboot') await systemService.reboot();
      else await systemService.shutdown();
      await modal.showAlert("Info", `Polecenie ${action === 'reboot' ? 'restartu' : 'wyłączenia'} wysłane.`);
    } catch (error) {
      await modal.showAlert("Błąd", "Błąd komunikacji z serwerem.");
    }
  };

  const handleBackup = async () => {
    const confirmed = await modal.showConfirm("Backup Danych", "Rozpocząć proces kopii zapasowej?");
    if (!confirmed) return;
    
    setBackupLoading(true);
    try {
      await systemService.startBackup();
      await modal.showAlert("Sukces", "Kopia zapasowa została zlecona w tle. Sprawdź logi systemowe.");
    } catch (error) {
      await modal.showAlert("Błąd", "Nie udało się uruchomić backupu.");
    } finally {
      setBackupLoading(false);
    }
  };

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Ustawienia</h1>
        <p className="text-slate-500">Zarządzanie serwerem NAS.</p>
      </div>

      <Card title="Aktywna Sesja">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center shrink-0">
              <User size={24} />
            </div>
            <div>
              <p className="text-sm text-slate-500">Zalogowany jako</p>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-slate-800">{user?.username || '...'}</h3>
              </div>
            </div>
          </div>
          <button onClick={handleLogout} className="flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-red-700 bg-red-50 hover:bg-red-100 border border-red-100 rounded-lg transition-colors w-full sm:w-auto">
            <LogOut size={16} /> Wyloguj się
          </button>
        </div>
      </Card>

      <Card title="Zasilanie Systemu">
        <div className="space-y-4">
          <div className="p-4 bg-orange-50 border border-orange-100 rounded-lg flex items-center gap-3">
            <AlertCircle className="text-orange-600 shrink-0" size={20} />
            <p className="text-sm text-orange-800 font-medium">Te operacje przerywają działanie usług.</p>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <button onClick={() => handlePowerAction('reboot')} className="flex items-center justify-center gap-2 p-4 border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-blue-300 hover:text-blue-700 transition-all font-medium text-slate-700">
              <RefreshCw size={20} /> Restart
            </button>
            <button onClick={() => handlePowerAction('shutdown')} className="flex items-center justify-center gap-2 p-4 border border-slate-200 rounded-lg hover:bg-red-50 hover:border-red-300 hover:text-red-700 transition-all font-medium text-slate-700">
              <Power size={20} /> Wyłącz
            </button>
          </div>
        </div>
      </Card>

      <Card title="Konserwacja Danych">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="bg-blue-50 p-2.5 rounded-lg text-blue-600"><Save size={24} /></div>
            <div>
              <h4 className="font-medium text-slate-800">Pełna Kopia Zapasowa</h4>
              <p className="text-xs text-slate-500">Zrzut plików i konfiguracji na dysk zapasowy.</p>
            </div>
          </div>
          <button onClick={handleBackup} disabled={backupLoading} className="w-full sm:w-auto px-6 py-2.5 bg-slate-800 text-white font-medium rounded-lg hover:bg-slate-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2">
            {backupLoading ? <RefreshCw className="animate-spin" size={18}/> : <ShieldCheck size={18} />}
            {backupLoading ? 'Przetwarzanie...' : 'Wykonaj Backup'}
          </button>
        </div>
      </Card>
    </div>
  );
};