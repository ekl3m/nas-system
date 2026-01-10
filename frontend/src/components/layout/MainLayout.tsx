import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Menu } from 'lucide-react';

export const Layout = () => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  return (
    <div className="flex h-screen bg-slate-50 overflow-hidden">
      
      {/* Sidebar z kontrolą stanu */}
      <Sidebar 
        isOpen={isSidebarOpen} 
        onClose={() => setIsSidebarOpen(false)} 
      />

      {/* Główna sekcja */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden relative">
        
        {/* Przycisk Menu (Widoczny TYLKO na mobile) */}
        <div className="md:hidden absolute top-4 left-4 z-10">
          <button 
            onClick={() => setIsSidebarOpen(true)}
            className="p-2 bg-white rounded-lg shadow-sm border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors"
          >
            <Menu size={24} />
          </button>
        </div>

        {/* Zawartość strony (Outlet) */}
        <main className="flex-1 overflow-x-hidden overflow-y-auto bg-slate-50 p-4 md:p-8 pt-16 md:pt-8 scroll-smooth">
          <div className="max-w-[1600px] mx-auto">
             <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};