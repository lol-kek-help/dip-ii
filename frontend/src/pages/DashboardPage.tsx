import { Card, Col, Row, Statistic, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { taskApi } from '../api/taskApi';
import { Task } from '../types/models';

export function DashboardPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  useEffect(() => { taskApi.list({ pageSize: 200, pageNumber: 0 }).then(setTasks).catch(() => setTasks([])); }, []);
  const newCount = tasks.filter((t) => t.status === 'NEW').length; const inWork = tasks.filter((t) => ['ASSIGNED','IN_PROGRESS','ESCALATED'].includes(t.status)).length;
  const closed = tasks.filter((t) => t.status === 'CLOSED').length;
  return <><Typography.Title level={3}>Дашборд</Typography.Title><Row gutter={16}><Col span={8}><Card><Statistic title='Новые' value={newCount} /></Card></Col><Col span={8}><Card><Statistic title='В работе' value={inWork} /></Card></Col><Col span={8}><Card><Statistic title='Закрытые' value={closed} /></Card></Col></Row></>;
}
