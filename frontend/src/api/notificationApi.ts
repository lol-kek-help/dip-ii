import { api } from './client';
import { Notification } from '../types/models';

export const notificationApi = {
  list: async () => (await api.get<Notification[]>('/notifications')).data,
  unreadCount: async () => (await api.get<{ unread: number }>('/notifications/unread-count')).data,
  markRead: async (id: number) => (await api.patch<Notification>(`/notifications/${id}/read`)).data,
  markAllRead: async () => { await api.patch('/notifications/read-all'); }
};
