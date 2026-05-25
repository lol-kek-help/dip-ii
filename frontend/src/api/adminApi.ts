import { api } from './client';

export interface AdminUser { id: number; name: string; username: string; role: string; }
export interface AuditLog { id: number; action: string; entityType: string; entityId: string; details: string; createdAt: string; }
export interface Dictionaries { statuses: string[]; priorities: string[]; categories: string[]; }

export const adminApi = {
  users: async () => (await api.get<AdminUser[]>('/admin/users')).data,
  audit: async (limit = 100) => (await api.get<AuditLog[]>('/admin/audit', { params: { limit } })).data,
  dictionaries: async () => (await api.get<Dictionaries>('/admin/dictionaries')).data
};
