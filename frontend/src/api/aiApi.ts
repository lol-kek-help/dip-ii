import { api } from './client';
import { ClassifyResponse, RecommendResponse, SimilarResponse } from '../types/models';

export const aiApi = {
  classify: async (text: string) => (await api.post<ClassifyResponse>('/ai/classify', { text })).data,
  similar: async (text: string) => (await api.post<SimilarResponse>('/ai/similar', { text })).data,
  recommend: async (text: string) => (await api.post<RecommendResponse>('/ai/recommend', { text })).data
};
