export type RoleName = 'USER' | 'OPERATOR' | 'ADMIN';
export type Status = 'NEW'|'UNASSIGNED'|'ASSIGNED'|'IN_PROGRESS'|'PENDING_USER'|'ESCALATED'|'RETURNED'|'RESOLVED'|'CLOSED'|'CANCELED';
export type Priority = 'LOW'|'MEDIUM'|'HIGH'|'URGENT';
export type Category = 'GENERAL'|'INCIDENT'|'ACCESS'|'BILLING';

export interface User { id: number; username: string; role: RoleName; name?: string; }
export interface Ticket { id: number; taskNumber?: string; title: string; description: string; status: Status; priority?: Priority; category?: Category; requester?: User; assignedTo?: User; createdAt?: string; resolutionDeadline?: string; resolutionComment?: string; }
export interface PageResponse<T> { items: T[]; pageNumber: number; pageSize: number; totalElements: number; totalPages: number; }
export interface AuthTokenResponse { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number; }

export interface SlaGroupMetric { name: string; total: number; violated: number; violationRate: number; avgFrtMinutes: number; avgMttrMinutes: number; }
export interface SlaDailyMetric { day: string; total: number; violated: number; violationRate: number; }
export interface TopViolatedTicket { ticketId: number; title: string; priority?: string; category?: string; mttrMinutes: number; allowedMinutes: number; }
export interface ExpiringTicket { ticketId: number; title: string; priority?: string; category?: string; deadline: string; assignedTo?: string; }
export interface SlaReport { total: number; violated: number; violationRate: number; avgFrtMinutes: number; avgMttrMinutes: number; medianFrtMinutes: number; medianMttrMinutes: number; p90FrtMinutes: number; p95FrtMinutes: number; p90MttrMinutes: number; p95MttrMinutes: number; misclassificationRate: number; returnedRate: number; slaViolationClosedRate: number; fcrRate: number; byCategory: SlaGroupMetric[]; byPriority: SlaGroupMetric[]; byOperator: SlaGroupMetric[]; daily: SlaDailyMetric[]; topViolatedTickets: TopViolatedTicket[]; expiringTickets: ExpiringTicket[]; }

export interface Explainability { mode: string; sources: string[]; llmStatus: string; rawModelOutput?: string | null; fallbackReason?: string | null; }
export interface ClassifyResponse { category: Category; priority: Priority; rationale: string; explainability?: Explainability; }
export interface SimilarItem { ticketId: number; title: string; score: number; }
export interface ResolvedCase { ticketId: number; title: string; fitPercent: number; resolutionComment?: string; }
export interface KnowledgeArticle { articleId: number; title: string; fitPercent: number; content: string; category: string; }
export interface SimilarResponse { tickets: SimilarItem[]; resolvedCases: ResolvedCase[]; articles: KnowledgeArticle[]; explainability?: Explainability; }
export type RecommendationMode = 'SHORT'|'STEP_BY_STEP'|'USER_REPLY'|'INTERNAL_COMMENT'|'TECHNICAL_GUIDE'|'ESCALATION_SUMMARY';
export interface RecommendResponse { recommendation: string; steps: string[]; explainability?: Explainability; }
export interface SavedAiRecommendation extends RecommendResponse { id: number; ticketId: number; mode?: string; sources?: string[]; llmStatus?: string; rawModelOutput?: string | null; fallbackReason?: string | null; accepted?: boolean; usefulnessScore?: number; feedbackComment?: string; createdAt: string; evaluatedAt?: string; createdBy?: User; evaluatedBy?: User; }
export interface AiQualityReport { totalRecommendations: number; evaluatedRecommendations: number; acceptedRecommendations: number; acceptanceRate: number; averageUsefulnessScore: number; classificationChanges: number; classificationChangeRate: number; }

export interface TicketComment { id: number; ticketId: number; author: User; commentText: string; internalComment: boolean; createdAt: string; updatedAt?: string; }
export interface TicketStatusHistory { id: number; ticketId: number; fromStatus?: Status; toStatus: Status; reason?: string; changedBy?: User; createdAt: string; }
export interface Notification { id: number; channel: string; subject: string; message: string; read: boolean; createdAt: string; }
