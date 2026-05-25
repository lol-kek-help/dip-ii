import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { parseJwt } from '../utils/jwt';
import { useAuthStore } from '../store/authStore';

export function LoginPage() {
  const [error, setError] = useState(''); const navigate = useNavigate(); const setAuth = useAuthStore((s) => s.setAuth);
  const onFinish = async (v: { username: string; password: string }) => {
    try { setError(''); const t = await authApi.login(v.username, v.password); const p = parseJwt(t.accessToken);
      setAuth({ accessToken: t.accessToken, refreshToken: t.refreshToken, username: p.sub ?? v.username, role: (p.role ?? 'USER') as never }); navigate('/');
    } catch { setError('Ошибка входа: проверьте логин и пароль.'); }
  };
  return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><Card style={{ width: 420 }}><Typography.Title level={3}>Вход в систему</Typography.Title>{error && <Alert type='error' message={error} style={{ marginBottom: 12 }} />}<Form layout='vertical' onFinish={onFinish}><Form.Item name='username' label='Логин' rules={[{ required: true }]}><Input /></Form.Item><Form.Item name='password' label='Пароль' rules={[{ required: true }]}><Input.Password /></Form.Item><Button block type='primary' htmlType='submit'>Войти</Button></Form></Card></div>;
}
