import { NavLink } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Files, 
  Film, 
  Activity, 
  ArrowRightLeft, 
  ScrollText, 
  Settings,
  Server,
  X 
} from 'lucide-react';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export const Sidebar = ({ isOpen, onClose }: SidebarProps) => {
  const navItems = [
    { icon: LayoutDashboard, label: 'Dashboard', path: '/' },
    { icon: Film, label: 'Multimedia', path: '/media' },
    { icon: Files, label: 'Pliki', path: '/files' },
    { icon: Activity, label: 'System', path: '/system' },
    { icon: ArrowRightLeft, label: 'Transfery', path: '/transfers' },
    { icon: ScrollText, label: 'Logi', path: '/logs' },
    { icon: Settings, label: 'Ustawienia', path: '/settings' },
  ];

  return (
    <>
      {/* TŁO (Backdrop) - Tylko na mobile, gdy otwarte */}
      {isOpen && (
        <div 
          className="fixed inset-0 z-20 bg-black/50 md:hidden backdrop-blur-sm transition-opacity"
          onClick={onClose}
        />
      )}

      {/* SIDEBAR */}
      <div className={`
        fixed md:static inset-y-0 left-0 z-30
        w-64 shrink-0                                 // <--- DODANO shrink-0 (Kluczowe dla desktopu)
        bg-slate-900 text-white 
        transform transition-transform duration-200 ease-in-out
        ${isOpen ? 'translate-x-0' : '-translate-x-full'}
        md:translate-x-0                              // <--- Resetuje pozycję na desktopie
        flex flex-col h-full shadow-xl
      `}>
        
        {/* Logo i Tytuł */}
        <div className="h-16 flex items-center justify-between px-6 bg-slate-950/50 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="bg-blue-600 p-1.5 rounded-lg">
              <Server size={20} className="text-white" />
            </div>
            <span className="text-lg font-bold tracking-wide">Home NAS</span>
          </div>
          
          {/* Przycisk zamknięcia (Tylko mobile) */}
          <button onClick={onClose} className="md:hidden text-slate-400 hover:text-white">
            <X size={24} />
          </button>
        </div>

        {/* Nawigacja */}
        <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              onClick={onClose}
              className={({ isActive }) => `
                flex items-center gap-3 px-3 py-3 rounded-lg transition-all duration-200 group
                ${isActive 
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-900/20' 
                  : 'text-slate-400 hover:bg-slate-800 hover:text-white'
                }
              `}
            >
              <item.icon size={20} className="shrink-0" />
              <span className="font-medium text-sm">{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Stopka */}
        <div className="p-4 border-t border-slate-800 bg-slate-950/30">
          <div className="flex items-center gap-3">
            <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-xs text-slate-500 font-mono">v1.0.0</span>
          </div>
        </div>
      </div>
    </>
  );
};