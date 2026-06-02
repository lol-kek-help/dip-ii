import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { RoleName } from '../types/models';

//хранилище состояния авторизации
type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  username: string | null;
  role: RoleName | null;
  setAuth: (a: {accessToken: string; refreshToken: string; username: string; role: RoleName}) => void;
  clear: () => void;
};

export const useAuthStore = create<AuthState>()(persist(
    (set) => ({
  accessToken: null, refreshToken: null, username: null, role: null,
  setAuth: (a) => set(a),
  clear: () => set({ accessToken: null, refreshToken: null, username: null, role: null })
}), { name: 'auth-store' }));
