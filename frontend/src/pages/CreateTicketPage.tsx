import { Button, Card, DatePicker, Form, Input, Select } from 'antd';
import { useEffect, useState } from 'react';
import { adminApi, AdminUser } from '../api/adminApi';
import { categoryLabel, priorityLabel } from '../utils/formatters';
import { useNavigate } from 'react-router-dom';
import { taskApi } from '../api/taskApi';

export function CreateTicketPage() {
  const nav = useNavigate();
  const [users, setUsers] = useState<AdminUser[]>([]);
  useEffect(() => { adminApi.users().then(setUsers).catch(() => setUsers([])); }, []);
  const onFinish = async (v: any) => {
    const created = await taskApi.create({ ...v, resolutionDeadline: v.resolutionDeadline?.toISOString() });
    nav(`/tickets/${created.id}`);
  };
  return <Card title='Создание обращения'><Form layout='vertical' onFinish={onFinish}><Form.Item name='title' label='Заголовок' rules={[{ required: true }]}><Input /></Form.Item><Form.Item name='description' label='Описание' rules={[{ required: true }]}><Input.TextArea rows={5} /></Form.Item><Form.Item name='requesterId' label='Автор обращения' rules={[{ required: true, message: 'Выберите автора' }]}><Select showSearch placeholder='Выберите пользователя' optionFilterProp='label' options={users.map((u) => ({ value: u.id, label: `${u.name} (${u.username})` }))} /></Form.Item><Form.Item name='category' label='Категория'><Select allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((v)=>({value:v,label:categoryLabel[v as keyof typeof categoryLabel]}))} /></Form.Item><Form.Item name='priority' label='Приоритет'><Select allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((v)=>({value:v,label:priorityLabel[v as keyof typeof priorityLabel]}))} /></Form.Item><Form.Item name='resolutionDeadline' label='Срок решения'><DatePicker showTime style={{ width: '100%' }} /></Form.Item><Button type='primary' htmlType='submit'>Создать</Button></Form></Card>;
}
