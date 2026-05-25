export type RoleName = 'USER' | 'OPERATOR' | 'ADMIN';
export type Status = 'NEW'|'UNASSIGNED'|'ASSIGNED'|'IN_PROGRESS'|'PENDING_USER'|'ESCALATED'|'RETURNED'|'RESOLVED'|'CLOSED'|'CANCELED';
export type Priority = 'LOW'|'MEDIUM'|'HIGH'|'URGENT';
export type Category = 'GENERAL'|'INCIDENT'|'ACCESS'|'BILLING';

export interface User { id: number; username: string; role: RoleName; name?: string; }
export interface Task { id: number; taskNumber?: string; title: string; descriprion: string; status: Status; priority?: Priority; category?: Category; requester?: User; assignedTo?: User; createdAt?: string; resolutionDeadline?: string; resolutionComment?: string; }
export interface AuthTokenResponse { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number; }
export interface SlaReport { total: number; breached: number; onTime: number; avgFrtMinutes: number; avgMttrMinutes: number; fcrPercent: number; }

export interface ClassifyResponse { category: Category; priority: Priority; rationale: string; }
export interface SimilarItem { ticketId: number; title: string; score: number; }
export interface ResolvedCase { ticketId: number; title: string; fitPercent: number; resolutionComment?: string; }
export interface SimilarResponse { similarTickets: SimilarItem[]; resolvedCases: ResolvedCase[]; knowledgeArticles: string[]; }
export interface RecommendResponse { recommendation: string; nextSteps: string[]; }
