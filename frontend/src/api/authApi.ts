import { api } from './client';
import { AuthTokenResponse } from '../types/models';

export const authApi = {
  login: async (username: string, password: string) => (await api.post<AuthTokenResponse>('/auth/login', { username, password })).data,
  logout: async (refreshToken: string) => { await api.post('/auth/logout', { refreshToken }); }
};
