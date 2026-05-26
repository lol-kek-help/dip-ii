import { Button, Card, DatePicker, Form, Input, Select, Space, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { taskApi } from '../api/taskApi';
import { useAuthStore } from '../store/authStore';
import { Task } from '../types/models';

export function TicketsPage() {
  const [tasks, setTasks] = useState<Task[]>([]); const [loading, setLoading] = useState(false); const role = useAuthStore((s) => s.role); const username = useAuthStore((s) => s.username);
  const load = async (v: any = {}) => { setLoading(true); try { const params: any = { ...v, pageSize: 50, pageNumber: 0 }; if (v.createdRange) { params.createdFrom = v.createdRange[0].toISOString(); params.createdTo = v.createdRange[1].toISOString(); delete params.createdRange; } const data = await taskApi.list(params); setTasks(role === 'USER' ? data.filter((t) => t.requester?.username === username) : data); } finally { setLoading(false); } };
  useEffect(() => { load(); }, []);
  return <Card title='Список обращений'><Form layout='inline' onFinish={load}><Form.Item name='status'><Select allowClear placeholder='Статус' style={{ width: 160 }} options={['NEW','ASSIGNED','IN_PROGRESS','CLOSED','ESCALATED'].map((s) => ({ value: s }))} /></Form.Item><Form.Item name='category'><Select allowClear placeholder='Категория' style={{ width: 140 }} options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((s) => ({ value: s }))} /></Form.Item><Form.Item name='priority'><Select allowClear placeholder='Приоритет' style={{ width: 140 }} options={['LOW','MEDIUM','HIGH','URGENT'].map((s) => ({ value: s }))} /></Form.Item><Form.Item name='createdRange'><DatePicker.RangePicker showTime /></Form.Item><Form.Item name='sortBy'><Select allowClear placeholder='Сортировать по' style={{ width: 180 }} options={['createdAt','updatedAt','resolutionDeadline','priority','status'].map((s) => ({ value: s }))}/></Form.Item><Form.Item name='sortDir'><Select allowClear placeholder='Направление' style={{ width: 140 }} options={[{value:'asc',label:'По возр.'},{value:'desc',label:'По убыв.'}]} /></Form.Item><Space><Button htmlType='submit' type='primary'>Применить</Button><Button onClick={() => load()}>Сброс</Button></Space></Form>
  <Table rowKey='id' loading={loading} style={{ marginTop: 16 }} dataSource={tasks} columns={[{ title:'ID', dataIndex:'id' },{ title:'Тема', dataIndex:'title', render:(_,r)=><Link to={`/tickets/${r.id}`}>{r.title}</Link> },{ title:'Статус', dataIndex:'status', render:(s)=><Tag>{s}</Tag>},{ title:'Приоритет', dataIndex:'priority' },{ title:'Категория', dataIndex:'category' }]} /></Card>;
}
