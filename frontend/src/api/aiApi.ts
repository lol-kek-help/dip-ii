import { api } from './client';
import { AiQualityReport, ClassifyResponse, RecommendResponse, RecommendationMode, SimilarResponse } from '../types/models';

export const aiApi = {
  classify: async (text: string) => (await api.post<ClassifyResponse>('/ai/classify', { text })).data,
  similar: async (text: string) => (await api.post<SimilarResponse>('/ai/similar', { text })).data,
  recommend: async (text: string, mode?: RecommendationMode, sourceHints?: string[]) => (await api.post<RecommendResponse>('/ai/recommend', { text, mode, sourceHints })).data,
  quality: async () => (await api.get<AiQualityReport>('/ai/quality')).data
};
