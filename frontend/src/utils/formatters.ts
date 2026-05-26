import { Category, Priority, Status } from '../types/models';

export const categoryLabel: Record<Category, string> = {
  GENERAL: 'Общие вопросы',
  INCIDENT: 'Инцидент',
  ACCESS: 'Доступы',
  BILLING: 'Биллинг'
};

export const priorityLabel: Record<Priority, string> = {
  LOW: 'Низкий',
  MEDIUM: 'Средний',
  HIGH: 'Высокий',
  URGENT: 'Критический'
};

export const statusLabel: Record<Status, string> = {
  NEW: 'Новая',
  UNASSIGNED: 'Не назначена',
  ASSIGNED: 'Назначена',
  IN_PROGRESS: 'В работе',
  PENDING_USER: 'Ожидает пользователя',
  ESCALATED: 'Эскалирована',
  RETURNED: 'Возвращена',
  RESOLVED: 'Решена',
  CLOSED: 'Закрыта',
  CANCELED: 'Отменена'
};
