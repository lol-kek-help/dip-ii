import { DashboardOutlined, FileTextOutlined, LogoutOutlined, PlusCircleOutlined } from '@ant-design/icons';
import { Layout, Menu, Typography } from 'antd';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { authApi } from '../api/authApi';

const { Header, Sider, Content } = Layout;
export function AppLayout() {
  const location = useLocation(); const navigate = useNavigate();
  const { role, refreshToken, clear } = useAuthStore();
  const items = [
    { key: '/', icon: <DashboardOutlined />, label: <Link to='/'>Дашборд</Link> },
    { key: '/tickets', icon: <FileTextOutlined />, label: <Link to='/tickets'>Обращения</Link> },
    { key: '/tickets/new', icon: <PlusCircleOutlined />, label: <Link to='/tickets/new'>Создать</Link> },
    { key: '/knowledge', label: <Link to='/knowledge'>База знаний</Link> },
    { key: '/analytics', label: <Link to='/analytics'>Аналитика</Link> },
    ...(role === 'ADMIN' ? [{ key: '/admin', label: <Link to='/admin'>Администрирование</Link> }] : [])
  ];
  const logout = async () => { try { if (refreshToken) await authApi.logout(refreshToken); } finally { clear(); navigate('/login'); } };
  return <Layout style={{ minHeight: '100vh' }}><Sider theme='light'><Typography.Title level={4} style={{ padding: 16, margin: 0 }}>Техподдержка</Typography.Title><Menu selectedKeys={[location.pathname]} items={items} /></Sider><Layout><Header style={{ background: '#fff', display: 'flex', justifyContent: 'end' }}><a onClick={logout}><LogoutOutlined /> Выход</a></Header><Content className='page'><Outlet /></Content></Layout></Layout>;
}
