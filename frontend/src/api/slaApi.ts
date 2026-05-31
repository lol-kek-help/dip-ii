import { api } from './client';
import { SlaReport } from '../types/models';
export const slaApi = { report: async (params?: { from?: string; to?: string }) => (await api.get<SlaReport>('/sla/report', { params })).data };
