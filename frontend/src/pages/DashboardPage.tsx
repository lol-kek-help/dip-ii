import { Card, Col, Row, Statistic, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { ticketApi } from '../api/ticketApi';
import { Ticket } from '../types/models';

export function DashboardPage() {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  useEffect(() => { ticketApi.list({ pageSize: 100, pageNumber: 0 }).then((page) => setTickets(page.items)).catch(() => setTickets([])); }, []);
  const newCount = tickets.filter((t) => t.status === 'NEW').length;
  const inWork = tickets.filter((t) => ['ASSIGNED','IN_PROGRESS','ESCALATED'].includes(t.status)).length;
  const closed = tickets.filter((t) => t.status === 'CLOSED').length;
  return <><Typography.Title level={3}>Дашборд</Typography.Title><Row gutter={16}><Col span={8}><Card><Statistic title='Новые' value={newCount} /></Card></Col><Col span={8}><Card><Statistic title='В работе' value={inWork} /></Card></Col><Col span={8}><Card><Statistic title='Закрытые' value={closed} /></Card></Col></Row></>;
}
