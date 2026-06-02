import { Button, Card, DatePicker, Form, Select, Space, Table, Tag } from 'antd';
import { Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ticketApi, TicketFilter } from '../api/ticketApi';
import { categoryLabel, priorityLabel, statusLabel } from '../utils/formatters';
import { Category, Priority, Status, Ticket } from '../types/models';

interface TicketFilterFormValues {
  status?: Status;
  category?: Category;
  priority?: Priority;
  createdRange?: [Dayjs, Dayjs];
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}
//состояние и загрузка списка обращений
export function TicketsPage() {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [form] = Form.useForm<TicketFilterFormValues>();
  //формирование параметров фильтрации
  const buildParams = (values: TicketFilterFormValues, nextPage = pageNumber,
                       nextSize = pageSize): TicketFilter => {
    const params: TicketFilter = { pageSize: nextSize, pageNumber: nextPage, sortBy: values.sortBy, sortDir: values.sortDir };
    if (values.status) params.status = values.status;
    if (values.category) params.category = values.category;
    if (values.priority) params.priority = values.priority;
    if (values.createdRange) {
      params.createdFrom = values.createdRange[0].toISOString();
      params.createdTo = values.createdRange[1].toISOString();
    }
    return params;
  };

  const load = async (nextPage = pageNumber, nextSize = pageSize) => {
    setLoading(true);
    try {
      const data = await ticketApi.list(buildParams(form.getFieldsValue(), nextPage, nextSize));
      setTickets(data.items);
      setTotal(data.totalElements);
      setPageNumber(data.pageNumber);
      setPageSize(data.pageSize);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(0, pageSize); }, []);

  return <Card title='Список обращений'>
    <Form form={form} layout='inline' onFinish={() => load(0, pageSize)}>
      <Form.Item name='status'><Select allowClear placeholder='Статус' style={{ width: 160 }} options={['NEW','ASSIGNED','IN_PROGRESS','PENDING_USER','ESCALATED','RETURNED','RESOLVED','CLOSED','CANCELED'].map((s) => ({ value: s, label: statusLabel[s as keyof typeof statusLabel] ?? s }))} /></Form.Item>
      <Form.Item name='category'><Select allowClear placeholder='Категория' style={{ width: 140 }} options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((s) => ({ value: s, label: categoryLabel[s as keyof typeof categoryLabel] }))} /></Form.Item>
      <Form.Item name='priority'><Select allowClear placeholder='Приоритет' style={{ width: 140 }} options={['LOW','MEDIUM','HIGH','URGENT'].map((s) => ({ value: s, label: priorityLabel[s as keyof typeof priorityLabel] }))} /></Form.Item>
      <Form.Item name='createdRange'><DatePicker.RangePicker showTime /></Form.Item>
      <Form.Item name='sortBy'><Select allowClear placeholder='Сортировать по' style={{ width: 180 }} options={['createdAt','updatedAt','resolutionDeadline','priority','status','category'].map((s) => ({ value: s }))}/></Form.Item>
      <Form.Item name='sortDir'><Select allowClear placeholder='Направление' style={{ width: 140 }} options={[{value:'asc',label:'По возр.'},{value:'desc',label:'По убыв.'}]} /></Form.Item>
      <Space><Button htmlType='submit' type='primary'>Применить</Button><Button onClick={() => { form.resetFields(); load(0, pageSize); }}>Сброс</Button></Space>
    </Form>
    {/*Таблица обращений*/}
    <Table<Ticket>
      rowKey='id'
      loading={loading}
      style={{ marginTop: 16 }}
      dataSource={tickets}
      pagination={{ current: pageNumber + 1, pageSize, total, showSizeChanger: true,
        onChange: (page, size) => load(page - 1, size) }}
      locale={{ emptyText: 'Обращения не найдены' }}
      columns={[
        { title:'ID', dataIndex:'id' },
        { title:'Тема', dataIndex:'title', render:(_,r)=><Link to={`/tickets/${r.id}`}>{r.title}</Link> },
        { title:'Статус', dataIndex:'status', render:(s: Status)=><Tag>{statusLabel[s] ?? s}</Tag>},
        { title:'Приоритет', dataIndex:'priority', render:(p: Priority)=> p ? (priorityLabel[p] ?? p) : '—' },
        { title:'Категория', dataIndex:'category', render:(c: Category)=> c ? (categoryLabel[c] ?? c) : '—' }
      ]}
    />
  </Card>;
}
