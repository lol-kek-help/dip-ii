import { api } from './client';

export interface KnowledgeArticle { id: number; title: string; content: string; category: string; createdAt?: string; }

export const knowledgeApi = {
  list: async (q?: string) => (await api.get<KnowledgeArticle[]>('/knowledge', { params: { q } })).data,
  getById: async (id: number) => (await api.get<KnowledgeArticle>(`/knowledge/${id}`)).data
};
