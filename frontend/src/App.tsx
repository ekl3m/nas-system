import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Layout } from './components/layout/MainLayout';
import { Dashboard } from './pages/Dashboard';
import { Files } from './pages/Files';
import { Login } from './pages/Login';
import { Media } from './pages/Media';
import { System } from './pages/System';
import { Transfers } from './pages/Transfers';
import { Logs } from './pages/Logs';
import { Settings } from './pages/Settings'
import { ModalProvider } from './context/ModalContext';
import { TransferProvider } from './context/TransferContext';
import { TransferWidget } from './components/TransferWidget';

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
};

function App() {
  return (
    <AuthProvider>
      <ModalProvider>
        <TransferProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/" element={
                <ProtectedRoute><Layout /></ProtectedRoute>
              }>
                <Route index element={<Dashboard />} />
                <Route path="media" element={<Media />} />
                <Route path="files" element={<Files />} />
                <Route path="system" element={<System />} />
                <Route path="transfers" element={<Transfers/>} />
                <Route path="logs" element={<Logs />} />
                <Route path="settings" element={<Settings />} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            <TransferWidget/>
          </BrowserRouter>
        </TransferProvider>
      </ModalProvider>
    </AuthProvider>
  );
}

export default App;