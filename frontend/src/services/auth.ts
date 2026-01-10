import { apiClient } from '../lib/axios';
import type { AuthResponse } from '../types/api';

interface LoginCredentials {
  username: string;
  password: string;
}

export const authService = {
  login: async (credentials: LoginCredentials): Promise<string> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', credentials);
    return response.data.token;
  },

  logout: async () => {
    // Zgodnie z obrazkiem: POST /api/auth/logout
    await apiClient.post('/auth/logout');
  }
};