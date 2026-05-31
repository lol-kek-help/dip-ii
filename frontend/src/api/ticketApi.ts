import { api } from './client';
import { PageResponse, SavedAiRecommendation, Status, Ticket, TicketComment, TicketStatusHistory } from '../types/models';

export type TicketFilter = Record<string, string | number | undefined>;
export interface CreateTicketPayload { title: string; description: string; priority?: string; category?: string; requesterId?: number; resolutionDeadline?: string; }
export interface CreateCommentPayload { commentText: string; internalComment: boolean; }

export const ticketApi = {
  list: async (filter: TicketFilter) => (await api.get<PageResponse<Ticket>>('/tickets', { params: filter })).data,
  getById: async (id: number) => (await api.get<Ticket>(`/tickets/${id}`)).data,
  create: async (payload: CreateTicketPayload) => (await api.post<Ticket>('/tickets', payload)).data,
  changeStatus: async (id: number, status: Status, reason?: string) => (await api.patch<Ticket>(`/tickets/${id}/status`, { status, reason })).data,
  updateClassification: async (id: number, category: string, priority: string) => (await api.patch<Ticket>(`/tickets/${id}/classification`, { category, priority })).data,
  assign: async (id: number, assigneeId: number) => (await api.patch<Ticket>(`/tickets/${id}/assignee`, { assigneeId })).data,
  escalate: async (id: number, reason: string) => (await api.patch<Ticket>(`/tickets/${id}/escalate`, { reason })).data,
  close: async (id: number, resolutionComment: string) => (await api.patch<Ticket>(`/tickets/${id}/close`, { resolutionComment })).data,
  comments: async (id: number) => (await api.get<TicketComment[]>(`/tickets/${id}/comments`)).data,
  addComment: async (id: number, payload: CreateCommentPayload) => (await api.post<TicketComment>(`/tickets/${id}/comments`, payload)).data,
  statusHistory: async (id: number) => (await api.get<TicketStatusHistory[]>(`/tickets/${id}/status-history`)).data,
  saveAiRecommendation: async (id: number) => (await api.post<SavedAiRecommendation>(`/tickets/${id}/ai/recommendations`)).data,
  aiRecommendations: async (id: number) => (await api.get<SavedAiRecommendation[]>(`/tickets/${id}/ai/recommendations`)).data,
  evaluateAiRecommendation: async (id: number, recommendationId: number, payload: { accepted: boolean; usefulnessScore?: number; feedbackComment?: string }) => (await api.patch<SavedAiRecommendation>(`/tickets/${id}/ai/recommendations/${recommendationId}/feedback`, payload)).data
};
