import { api } from './client';
import { SlaReport } from '../types/models';
export const slaApi = { report: async () => (await api.get<SlaReport>('/sla/report')).data };
