import { RoleName } from '../types/models';
export function parseJwt(token: string): { sub?: string; role?: RoleName } {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return { sub: payload.sub, role: payload.role };
  } catch {
    return {};
  }
}
