import axios from 'axios';
import { useAuthStore } from '../store/authStore';

export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080' });

let isRefreshing = false;
let queue: Array<(token: string | null) => void> = [];

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use((r) => r, async (error) => {
  const original = error.config;
  if (error.response?.status === 401 && !original._retry) {
    original._retry = true;
    const { refreshToken, clear, setAuth, username, role } = useAuthStore.getState();
    if (!refreshToken) { clear(); throw error; }
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        queue.push((token) => token ? resolve(api(original)) : reject(error));
      });
    }
    isRefreshing = true;
    try {
      const res = await axios.post(`${api.defaults.baseURL}/auth/refresh`, { refreshToken });
      const next = res.data as { accessToken: string; refreshToken: string };
      setAuth({ accessToken: next.accessToken, refreshToken: next.refreshToken, username: username!, role: role! });
      queue.forEach((cb) => cb(next.accessToken)); queue = [];
      return api(original);
    } catch (e) { queue.forEach((cb) => cb(null)); queue=[]; clear(); throw e; }
    finally { isRefreshing = false; }
  }
  throw error;
});
