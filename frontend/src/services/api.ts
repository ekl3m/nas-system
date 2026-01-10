import { apiClient } from '../lib/axios';
import type { 
  SystemStats, 
  FileInfo, 
  FileOperationResponse,
  CreateFolderRequest,
  MoveRequest,
  RestoreRequest
} from '../types/api';

// --- SYSTEM ---
export const systemService = {
  getStats: async (): Promise<SystemStats> => {
    const response = await apiClient.get<SystemStats>('/system/stats');
    return response.data;
  },
  
  reboot: async () => {
    return apiClient.post('/system/reboot');
  },

  shutdown: async () => {
    return apiClient.post('/system/shutdown');
  },

  startBackup: async () => {
    return apiClient.post('/system/backup/start');
  },

  // Logi (zwracają listę stringów)
  getEventLogs: async (): Promise<string[]> => {
    const response = await apiClient.get<string[]>('/system/logs/events');
    return response.data;
  },

  getTransferLogs: async (): Promise<string[]> => {
    const response = await apiClient.get<string[]>('/system/logs/transfers');
    return response.data;
  },

  getSystemLogs: async (): Promise<string[]> => {
    const response = await apiClient.get<string[]>('/system/logs/system');
    return response.data;
  }
};

// --- FILES ---
export const fileService = {
  listFiles: async (path: string = ''): Promise<FileInfo[]> => {
    const response = await apiClient.get<FileInfo[]>('/files/list', {
      params: { path }
    });
    return response.data;
  },

  getRecent: async (limit: number = 5, multimediaOnly: boolean = false): Promise<FileInfo[]> => {
    const response = await apiClient.get<FileInfo[]>('/files/recent', {
      params: { limit, multimediaOnly }
    });
    return response.data;
  },

  createFolder: async (logicalPath: string): Promise<FileOperationResponse> => {
    const body: CreateFolderRequest = { logicalPath };
    const response = await apiClient.post<FileOperationResponse>('/files/folders/create', body);
    return response.data;
  },

  upload: async (path: string, file: File, onProgress?: (percent: number) => void): Promise<void> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('path', path);

    await apiClient.post('/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percent);
        }
      }
    });
  },

  delete: async (path: string, permanent: boolean = false): Promise<FileOperationResponse> => {
    const response = await apiClient.delete<FileOperationResponse>('/files/delete', {
      params: { path, permanent }
    });
    return response.data;
  },

  move: async (fromPath: string, toPath: string): Promise<FileOperationResponse> => {
    const body: MoveRequest = { fromPath, toPath };
    const response = await apiClient.put<FileOperationResponse>('/files/move', body);
    return response.data;
  },

  restore: async (logicalPath: string): Promise<FileOperationResponse> => {
    const body: RestoreRequest = { logicalPath };
    const response = await apiClient.post<FileOperationResponse>('/files/restore', body);
    return response.data;
  },

  download: async (path: string): Promise<void> => {
    const response = await apiClient.get('/files/download', {
      params: { path },
      responseType: 'blob',
    });

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    
    const filename = path.split('/').pop() || 'download';
    link.setAttribute('download', filename);
    
    document.body.appendChild(link);
    link.click();
    link.remove();
  },

  getFileBlob: async (path: string): Promise<Blob> => {
    const response = await apiClient.get('/files/download', {
      params: { path },
      responseType: 'blob',
    });
    return response.data;
  },


  // --- SEKJA KOSZA ---

  getTrashFiles: async (): Promise<FileInfo[]> => {
    try {
      const response = await apiClient.get('/files/list', {
        params: { path: 'trash' } 
      });
      return response.data;
    } catch (error) {
      return [];
    }
  },

  restoreFile: async (logicalPath: string): Promise<void> => {
    await apiClient.post('/files/restore', { 
      logicalPath: logicalPath // Backend wymaga pełnej ścieżki np. "admin/trash/plik.txt"
    });
  },

  deletePermanently: async (fileName: string): Promise<void> => {
    const relativePath = `trash/${fileName}`;
    
    await apiClient.delete('/files/delete', {
      params: { 
        path: relativePath, 
        permanent: true 
      }
    });
  },

  emptyTrash: async (): Promise<void> => {
    const trashFiles = await fileService.getTrashFiles();
    
    const deletePromises = trashFiles.map(file => 
      fileService.deletePermanently(file.name)
    );
    
    await Promise.all(deletePromises);
  }


};

export const authService = {
  login: async (username: string, password: string) => {
    const response = await apiClient.post('/auth/login', { username, password });
    
    return response.data;
  }
};