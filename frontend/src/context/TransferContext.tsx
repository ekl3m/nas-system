import { createContext, useContext, useState, type ReactNode, useCallback } from 'react';
import { fileService } from '../services/api';

export interface TransferItem {
  id: string;
  name: string;
  progress: number; // 0-100
  status: 'pending' | 'uploading' | 'completed' | 'error';
  error?: string;
}

interface TransferContextType {
  transfers: TransferItem[];
  addUpload: (path: string, file: File, onSuccess?: () => void) => Promise<void>;
  clearCompleted: () => void;
  isWidgetOpen: boolean;
  toggleWidget: () => void;
}

const TransferContext = createContext<TransferContextType | undefined>(undefined);

export const TransferProvider = ({ children }: { children: ReactNode }) => {
  const [transfers, setTransfers] = useState<TransferItem[]>([]);
  const [isWidgetOpen, setIsWidgetOpen] = useState(false);

  const updateTransfer = (id: string, updates: Partial<TransferItem>) => {
    setTransfers(prev => prev.map(t => t.id === id ? { ...t, ...updates } : t));
  };

  const addUpload = useCallback(async (path: string, file: File, onSuccess?: () => void) => {
    const id = Date.now().toString() + Math.random().toString(36).substr(2, 9);
    
    const newItem: TransferItem = {
      id,
      name: file.name,
      progress: 0,
      status: 'pending'
    };

    setTransfers(prev => [newItem, ...prev]);
    setIsWidgetOpen(true); 

    try {
      updateTransfer(id, { status: 'uploading' });
      
      await fileService.upload(path, file, (percent) => {
        updateTransfer(id, { progress: percent });
      });

      updateTransfer(id, { status: 'completed', progress: 100 });
      if (onSuccess) onSuccess();
      
    } catch (error) {
      console.error("Upload error", error);
      updateTransfer(id, { status: 'error', error: 'Błąd przesyłania' });
    }
  }, []);

  const clearCompleted = () => {
    setTransfers(prev => prev.filter(t => t.status === 'uploading' || t.status === 'pending'));
  };

  const toggleWidget = () => setIsWidgetOpen(prev => !prev);

  return (
    <TransferContext.Provider value={{ transfers, addUpload, clearCompleted, isWidgetOpen, toggleWidget }}>
      {children}
    </TransferContext.Provider>
  );
};

export const useTransfer = () => {
  const context = useContext(TransferContext);
  if (!context) throw new Error('useTransfer must be used within a TransferProvider');
  return context;
};