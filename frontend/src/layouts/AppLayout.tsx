import { BellOutlined, DashboardOutlined, FileTextOutlined, LogoutOutlined, PlusCircleOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer, Layout, List, Menu, Space, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { authApi } from '../api/authApi';
import { notificationApi } from '../api/notificationApi';
import { Notification } from '../types/models';

const { Header, Sider, Content } = Layout;
export function AppLayout() {
  const location = useLocation(); const navigate = useNavigate();
  const { role, refreshToken, clear } = useAuthStore();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const canOperate = role === 'OPERATOR' || role === 'ADMIN';
  const items = [
    { key: '/', icon: <DashboardOutlined />, label: <Link to='/'>Дашборд</Link> },
    { key: '/tickets', icon: <FileTextOutlined />, label: <Link to='/tickets'>Обращения</Link> },
    { key: '/tickets/new', icon: <PlusCircleOutlined />, label: <Link to='/tickets/new'>Создать</Link> },
    { key: '/knowledge', label: <Link to='/knowledge'>База знаний</Link> },
    ...(canOperate ? [{ key: '/analytics', label: <Link to='/analytics'>Аналитика</Link> }] : []),
    ...(role === 'ADMIN' ? [{ key: '/admin', label: <Link to='/admin'>Администрирование</Link> }] : [])
  ];

  const loadNotifications = async () => setNotifications(await notificationApi.list());
  useEffect(() => { loadNotifications().catch(() => setNotifications([])); }, []);
  const unread = notifications.filter((n) => !n.read).length;
  const logout = async () => { try { if (refreshToken) await authApi.logout(refreshToken); } finally { clear(); navigate('/login'); } };

  return <Layout style={{ minHeight: '100vh' }}><Sider theme='light'><Typography.Title level={4} style={{ padding: 16, margin: 0 }}>Техподдержка</Typography.Title><Menu selectedKeys={[location.pathname]} items={items} /></Sider><Layout><Header style={{ background: '#fff', display: 'flex', justifyContent: 'end' }}><Space>
    <Badge count={unread}><Button icon={<BellOutlined />} onClick={() => { setDrawerOpen(true); loadNotifications(); }}>Уведомления</Button></Badge>
    <a onClick={logout}><LogoutOutlined /> Выход</a>
  </Space></Header><Content className='page'><Outlet /></Content>
    <Drawer title='Уведомления' open={drawerOpen} onClose={() => setDrawerOpen(false)} extra={<Button onClick={async () => { await notificationApi.markAllRead(); await loadNotifications(); }}>Прочитать все</Button>}>
      <List dataSource={notifications} locale={{ emptyText: 'Уведомлений нет' }} renderItem={(item) => <List.Item actions={!item.read ? [<Button key='read' type='link' onClick={async () => { await notificationApi.markRead(item.id); await loadNotifications(); }}>Прочитано</Button>] : []}>
        <List.Item.Meta title={<Space>{!item.read && <Badge status='processing' />}{item.subject}</Space>} description={<><div>{item.message}</div><Typography.Text type='secondary'>{item.createdAt}</Typography.Text></>} />
      </List.Item>} />
    </Drawer>
  </Layout></Layout>;
}
