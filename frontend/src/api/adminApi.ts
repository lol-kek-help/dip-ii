import { api } from './client';
import { PageResponse } from '../types/models';

export interface AdminUser { id: number; name: string; username: string; role: string; }
export interface AuditLog { id: number; action: string; entityType: string; entityId: string; details: string; beforeValue?: string; afterValue?: string; ipAddress?: string; userAgent?: string; createdAt: string; actor?: AdminUser; }
export interface Dictionaries { statuses: string[]; priorities: string[]; categories: string[]; }
export interface AuditFilter { pageNumber?: number; pageSize?: number; action?: string; entityType?: string; actorId?: number; createdFrom?: string; createdTo?: string; }

export const adminApi = {
  users: async () => (await api.get<AdminUser[]>('/admin/users')).data,
  audit: async (filter: AuditFilter = { pageNumber: 0, pageSize: 50 }) => (await api.get<PageResponse<AuditLog>>('/admin/audit', { params: filter })).data,
  dictionaries: async () => (await api.get<Dictionaries>('/admin/dictionaries')).data
};
