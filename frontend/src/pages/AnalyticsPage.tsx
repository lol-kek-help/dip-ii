import { Card, Col, DatePicker, Row, Statistic, Table, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { slaApi } from '../api/slaApi';
import { SlaDailyMetric, SlaGroupMetric, SlaReport } from '../types/models';

function BarChart({ data, valueKey }: { data: SlaGroupMetric[]; valueKey: 'total' | 'violated' | 'violationRate' }) {
  const max = Math.max(1, ...data.map((d) => Number(d[valueKey])));
  return <div>{data.map((item) => <div key={item.name} style={{ marginBottom: 8 }}><Typography.Text>{item.name}: {Number(item[valueKey]).toFixed(valueKey === 'violationRate' ? 1 : 0)}</Typography.Text><div style={{ background: '#f0f0f0', height: 10 }}><div style={{ width: `${Number(item[valueKey]) / max * 100}%`, height: 10, background: '#1677ff' }} /></div></div>)}</div>;
}

function DailyChart({ data }: { data: SlaDailyMetric[] }) {
  const max = Math.max(1, ...data.map((d) => d.violated));
  return <div style={{ display: 'flex', gap: 6, alignItems: 'end', minHeight: 120 }}>{data.map((d) => <div key={d.day} style={{ textAlign: 'center', flex: 1 }}><div title={`${d.day}: ${d.violated}`} style={{ height: `${Math.max(4, d.violated / max * 100)}px`, background: '#ff4d4f' }} /><Typography.Text style={{ fontSize: 10 }}>{d.day.slice(5)}</Typography.Text></div>)}</div>;
}

export function AnalyticsPage(){
  const [report,setReport]=useState<SlaReport>();
  const load = (range?: [import('dayjs').Dayjs, import('dayjs').Dayjs]) => slaApi.report(range ? { from: range[0].toISOString(), to: range[1].toISOString() } : undefined).then(setReport).catch(()=>setReport(undefined));
  useEffect(()=>{load();},[]);
  return <><Typography.Title level={3}>Аналитика SLA</Typography.Title>
    <Card style={{ marginBottom: 16 }}><DatePicker.RangePicker showTime onChange={(range) => load(range && range[0] && range[1] ? [range[0], range[1]] : undefined)} /></Card>
    <Row gutter={[16,16]}>
      <Col span={6}><Card><Statistic title='Всего SLA-записей' value={report?.total ?? 0}/></Card></Col>
      <Col span={6}><Card><Statistic title='Нарушено SLA' value={report?.violated ?? 0}/></Card></Col>
      <Col span={6}><Card><Statistic title='K SLA violation, %' value={report?.slaViolationClosedRate ?? 0} precision={2}/></Card></Col>
      <Col span={6}><Card><Statistic title='FCR, %' value={report?.fcrRate ?? 0} precision={2}/></Card></Col>
      <Col span={6}><Card><Statistic title='K misclass, %' value={report?.misclassificationRate ?? 0} precision={2}/></Card></Col>
      <Col span={6}><Card><Statistic title='K returned, %' value={report?.returnedRate ?? 0} precision={2}/></Card></Col>
      <Col span={6}><Card><Statistic title='FRT, мин' value={report?.avgFrtMinutes ?? 0} precision={1}/></Card></Col>
      <Col span={6}><Card><Statistic title='MTTR, мин' value={report?.avgMttrMinutes ?? 0} precision={1}/></Card></Col>
      <Col span={6}><Card><Statistic title='Median FRT' value={report?.medianFrtMinutes ?? 0} precision={1}/></Card></Col>
      <Col span={6}><Card><Statistic title='Median MTTR' value={report?.medianMttrMinutes ?? 0} precision={1}/></Card></Col>
      <Col span={6}><Card><Statistic title='P95 FRT' value={report?.p95FrtMinutes ?? 0} precision={1}/></Card></Col>
      <Col span={6}><Card><Statistic title='P95 MTTR' value={report?.p95MttrMinutes ?? 0} precision={1}/></Card></Col>
      <Col span={12}><Card title='Нарушения SLA по дням'><DailyChart data={report?.daily ?? []} /></Card></Col>
      <Col span={12}><Card title='SLA по категориям'><BarChart data={report?.byCategory ?? []} valueKey='violationRate' /></Card></Col>
      <Col span={12}><Card title='SLA по операторам'><BarChart data={report?.byOperator ?? []} valueKey='violated' /></Card></Col>
      <Col span={12}><Card title='SLA по приоритетам'><BarChart data={report?.byPriority ?? []} valueKey='violationRate' /></Card></Col>
      <Col span={24}><Card title='Обращения с истекающим SLA'><Table rowKey='ticketId' dataSource={report?.expiringTickets ?? []} pagination={false} columns={[{title:'ID',dataIndex:'ticketId'},{title:'Тема',dataIndex:'title'},{title:'Приоритет',dataIndex:'priority'},{title:'Категория',dataIndex:'category'},{title:'Дедлайн',dataIndex:'deadline'},{title:'Исполнитель',dataIndex:'assignedTo'}]} /></Card></Col>
      <Col span={24}><Card title='Top нарушенных обращений'><Table rowKey='ticketId' dataSource={report?.topViolatedTickets ?? []} pagination={false} columns={[{title:'ID',dataIndex:'ticketId'},{title:'Тема',dataIndex:'title'},{title:'MTTR, мин',dataIndex:'mttrMinutes'},{title:'SLA, мин',dataIndex:'allowedMinutes'}]} /></Card></Col>
    </Row>
  </>;
}
