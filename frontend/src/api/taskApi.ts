import { api } from './client';
import { Task } from '../types/models';

export type TaskFilter = Record<string, string | number | undefined>;

export const taskApi = {
  list: async (filter: TaskFilter) => (await api.get<Task[]>('/tickets', { params: filter })).data,
  getById: async (id: number) => (await api.get<Task>(`/tickets/${id}`)).data,
  create: async (payload: unknown) => (await api.post<Task>('/tickets', payload)).data,
  changeStatus: async (id: number, status: string, reason?: string) => (await api.patch<Task>(`/tickets/${id}/status`, { status, reason })).data,
  updateClassification: async (id: number, category: string, priority: string) => (await api.patch<Task>(`/tickets/${id}/classification`, { category, priority })).data,
  assign: async (id: number, assigneeId: number) => (await api.patch<Task>(`/tickets/${id}/assignee`, { assigneeId })).data,
  escalate: async (id: number, reason?: string) => (await api.patch<Task>(`/tickets/${id}/escalate`, { reason })).data,
  close: async (id: number, resolutionComment?: string) => (await api.patch<Task>(`/tickets/${id}/close`, { resolutionComment })).data
};
