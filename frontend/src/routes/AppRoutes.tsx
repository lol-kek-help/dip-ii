import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { LoginPage } from '../pages/LoginPage';
import { DashboardPage } from '../pages/DashboardPage';
import { TicketsPage } from '../pages/TicketsPage';
import { TicketDetailsPage } from '../pages/TicketDetailsPage';
import { CreateTicketPage } from '../pages/CreateTicketPage';
import { KnowledgePage } from '../pages/KnowledgePage';
import { AnalyticsPage } from '../pages/AnalyticsPage';
import { AdminPage } from '../pages/AdminPage';
import { useAuthStore } from '../store/authStore';
//проверка токена и авторизации
function Guard({ children, roles }: { children: JSX.Element; roles?: string[] }) {
  const { accessToken, role } = useAuthStore();
  if (!accessToken) return <Navigate to='/login' replace />;
  if (roles && (!role || !roles.includes(role))) return <Navigate to='/' replace />;
  return children;
}
//маршрутизация страниц
export function AppRoutes() {
  return <Routes>
    <Route path='/login' element={<LoginPage />} />
    <Route path='/' element={<Guard><AppLayout /></Guard>}>
      <Route index element={<DashboardPage />} />
      <Route path='tickets' element={<TicketsPage />} />
      <Route path='tickets/new' element={<CreateTicketPage />} />
      <Route path='tickets/:id' element={<TicketDetailsPage />} />
      <Route path='knowledge' element={<KnowledgePage />} />
      <Route path='analytics' element={<Guard roles={['OPERATOR', 'ADMIN']}><AnalyticsPage /></Guard>} />
      <Route path='admin' element={<Guard roles={['ADMIN']}><AdminPage /></Guard>} />
    </Route>
  </Routes>;
}
