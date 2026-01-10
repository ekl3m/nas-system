import { createContext, useContext, useState, type ReactNode, useCallback } from 'react';
import { Modal, type ModalType } from '../components/ui/Modal';

interface ModalContextType {
  showAlert: (title: string, message: string) => Promise<void>;
  showConfirm: (title: string, message: string) => Promise<boolean>;
  showPrompt: (title: string, message: string, defaultValue?: string, placeholder?: string) => Promise<string | null>;
}

const ModalContext = createContext<ModalContextType | undefined>(undefined);

export const ModalProvider = ({ children }: { children: ReactNode }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [modalConfig, setModalConfig] = useState<{
    type: ModalType;
    title: string;
    message?: string;
    placeholder?: string;
  }>({ type: 'alert', title: '' });

  const [inputValue, setInputValue] = useState('');
  
  const [resolver, setResolver] = useState<{ resolve: (value: any) => void } | null>(null);

  const openModal = (type: ModalType, title: string, message?: string, initialInput: string = '', placeholder: string = '') => {
    setModalConfig({ type, title, message, placeholder });
    setInputValue(initialInput);
    setIsOpen(true);
  };

  const handleConfirm = (value?: string) => {
    setIsOpen(false);
    if (resolver) {
      if (modalConfig.type === 'confirm') resolver.resolve(true);
      else if (modalConfig.type === 'prompt') resolver.resolve(value);
      else resolver.resolve(true);
    }
  };

  const handleCancel = () => {
    setIsOpen(false);
    if (resolver) {
      if (modalConfig.type === 'confirm') resolver.resolve(false);
      else if (modalConfig.type === 'prompt') resolver.resolve(null);
      else resolver.resolve(true);
    }
  };

  // --- API ---

  const showAlert = useCallback((title: string, message: string): Promise<void> => {
    return new Promise((resolve) => {
      setResolver({ resolve });
      openModal('alert', title, message);
    });
  }, []);

  const showConfirm = useCallback((title: string, message: string): Promise<boolean> => {
    return new Promise((resolve) => {
      setResolver({ resolve });
      openModal('confirm', title, message);
    });
  }, []);

  const showPrompt = useCallback((title: string, message: string, defaultValue = '', placeholder = ''): Promise<string | null> => {
    return new Promise((resolve) => {
      setResolver({ resolve });
      openModal('prompt', title, message, defaultValue, placeholder);
    });
  }, []);

  return (
    <ModalContext.Provider value={{ showAlert, showConfirm, showPrompt }}>
      {children}
      <Modal
        isOpen={isOpen}
        type={modalConfig.type}
        title={modalConfig.title}
        message={modalConfig.message}
        inputValue={inputValue}
        placeholder={modalConfig.placeholder}
        setInputValue={setInputValue}
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      />
    </ModalContext.Provider>
  );
};

export const useModal = () => {
  const context = useContext(ModalContext);
  if (!context) throw new Error('useModal must be used within a ModalProvider');
  return context;
};