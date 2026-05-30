import { api } from './client';
import { User } from '../types/models';

export const userApi = {
  me: async () => (await api.get<User>('/users/me')).data,
  users: async () => (await api.get<User[]>('/users')).data
};
