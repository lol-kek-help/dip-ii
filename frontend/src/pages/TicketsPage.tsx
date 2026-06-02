import { Button, Card, DatePicker, Form, Select, Space, Table, Tag, Typography } from 'antd';
import { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ticketApi, TicketFilter } from '../api/ticketApi';
import { categoryLabel, priorityColor, priorityLabel, statusColor, statusLabel } from '../utils/formatters';
import { Category, Priority, Status, Ticket } from '../types/models';

interface TicketFilterFormValues {
  status?: Status;
  category?: Category;
  priority?: Priority;
  createdRange?: [Dayjs, Dayjs];
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

const statusOptions: Status[] = ['NEW','ASSIGNED','IN_PROGRESS','PENDING_USER','ESCALATED','RETURNED','RESOLVED','CLOSED','CANCELED'];
const categoryOptions: Category[] = ['GENERAL','INCIDENT','ACCESS','BILLING'];
const priorityOptions: Priority[] = ['LOW','MEDIUM','HIGH','URGENT'];

function deadlineTag(deadline?: string) {
  if (!deadline) return <Typography.Text type='secondary'>—</Typography.Text>;
  const date = dayjs(deadline);
  const hoursLeft = date.diff(dayjs(), 'hour');
  const color = hoursLeft < 0 ? 'red' : hoursLeft <= 24 ? 'orange' : 'green';
  const label = hoursLeft < 0 ? 'Просрочено' : hoursLeft <= 24 ? 'Скоро' : 'В срок';
  return <Space direction='vertical' size={0}>
    <Typography.Text>{date.format('DD.MM.YYYY HH:mm')}</Typography.Text>
    <Tag color={color}>{label}</Tag>
  </Space>;
}

//состояние и загрузка списка обращений
export function TicketsPage() {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [filtersCollapsed, setFiltersCollapsed] = useState(false);
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

  const load = async (nextPage = pageNumber, nextSize = pageSize, values: TicketFilterFormValues = form.getFieldsValue()) => {
    setLoading(true);
    try {
      const data = await ticketApi.list(buildParams(values, nextPage, nextSize));
      setTickets(data.items);
      setTotal(data.totalElements);
      setPageNumber(data.pageNumber);
      setPageSize(data.pageSize);
    } finally {
      setLoading(false);
    }
  };

  const applyPreset = (values: Partial<TicketFilterFormValues>) => {
    const nextValues = { ...form.getFieldsValue(), ...values };
    form.setFieldsValue(nextValues);
    load(0, pageSize, nextValues);
  };

  useEffect(() => { load(0, pageSize); }, []);

  return <Space direction='vertical' size={16} style={{ width: '100%' }}>
    <Card>
      <Space direction='vertical' size={12} style={{ width: '100%' }}>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>Обращения</Typography.Title>
            <Typography.Text type='secondary'>Быстрая очередь с фильтрами, SLA и ответственными.</Typography.Text>
          </div>
          <Button onClick={() => setFiltersCollapsed(!filtersCollapsed)}>{filtersCollapsed ? 'Показать фильтры' : 'Скрыть фильтры'}</Button>
        </Space>
        <Space wrap>
          <Button onClick={() => applyPreset({ status: 'NEW', sortBy: 'createdAt', sortDir: 'desc' })}>Новые</Button>
          <Button onClick={() => applyPreset({ status: 'IN_PROGRESS', sortBy: 'resolutionDeadline', sortDir: 'asc' })}>В работе</Button>
          <Button onClick={() => applyPreset({ priority: 'URGENT', sortBy: 'resolutionDeadline', sortDir: 'asc' })}>Критичные</Button>
          <Button onClick={() => applyPreset({ status: 'ESCALATED', sortBy: 'updatedAt', sortDir: 'desc' })}>Эскалированные</Button>
          <Button onClick={() => applyPreset({ status: 'CLOSED', sortBy: 'updatedAt', sortDir: 'desc' })}>Закрытые</Button>
        </Space>
        {!filtersCollapsed && <Form form={form} layout='inline' onFinish={() => load(0, pageSize)} className='ticket-filters'>
          <Form.Item name='status' label='Статус'><Select allowClear style={{ width: 170 }} options={statusOptions.map((s) => ({ value: s, label: statusLabel[s] ?? s }))} /></Form.Item>
          <Form.Item name='category' label='Категория'><Select allowClear style={{ width: 160 }} options={categoryOptions.map((s) => ({ value: s, label: categoryLabel[s] }))} /></Form.Item>
          <Form.Item name='priority' label='Приоритет'><Select allowClear style={{ width: 150 }} options={priorityOptions.map((s) => ({ value: s, label: priorityLabel[s] }))} /></Form.Item>
          <Form.Item name='createdRange' label='Создано'><DatePicker.RangePicker showTime /></Form.Item>
          <Form.Item name='sortBy' label='Сортировать'><Select allowClear style={{ width: 180 }} options={['createdAt','updatedAt','resolutionDeadline','priority','status','category'].map((s) => ({ value: s }))}/></Form.Item>
          <Form.Item name='sortDir'><Select allowClear placeholder='Направление' style={{ width: 140 }} options={[{value:'asc',label:'По возр.'},{value:'desc',label:'По убыв.'}]} /></Form.Item>
          <Space><Button htmlType='submit' type='primary'>Применить</Button><Button onClick={() => { form.resetFields(); load(0, pageSize); }}>Сброс</Button></Space>
        </Form>}
      </Space>
    </Card>
    {/*Таблица обращений*/}
    <Card>
      <Table<Ticket>
        rowKey='id'
        loading={loading}
        dataSource={tickets}
        scroll={{ x: 1100 }}
        pagination={{ current: pageNumber + 1, pageSize, total, showSizeChanger: true,
          showTotal: (value) => `Найдено обращений: ${value}`,
          onChange: (page, size) => load(page - 1, size) }}
        locale={{ emptyText: 'Обращения не найдены' }}
        columns={[
          { title:'ID', dataIndex:'id', width: 80, fixed: 'left' },
          { title:'Тема', dataIndex:'title', width: 300, render:(_,r)=><Link to={`/tickets/${r.id}`}>{r.title}</Link> },
          { title:'Статус', dataIndex:'status', width: 160, render:(s: Status)=><Tag color={statusColor[s]}>{statusLabel[s] ?? s}</Tag>},
          { title:'Приоритет', dataIndex:'priority', width: 140, render:(p: Priority)=> p ? <Tag color={priorityColor[p]}>{priorityLabel[p] ?? p}</Tag> : '—' },
          { title:'Категория', dataIndex:'category', width: 160, render:(c: Category)=> c ? (categoryLabel[c] ?? c) : '—' },
          { title:'Исполнитель', dataIndex:'assignedTo', width: 180, render:(_, r) => r.assignedTo?.name || r.assignedTo?.username || <Typography.Text type='secondary'>Не назначен</Typography.Text> },
          { title:'Дедлайн / SLA', dataIndex:'resolutionDeadline', width: 190, render: deadlineTag },
          { title:'Создано', dataIndex:'createdAt', width: 170, render:(value?: string) => value ? dayjs(value).format('DD.MM.YYYY HH:mm') : '—' },
          { title:'Действия', key:'actions', width: 110, fixed: 'right', render:(_, r) => <Button type='link' size='small'><Link to={`/tickets/${r.id}`}>Открыть</Link></Button> }
        ]}
      />
    </Card>
  </Space>;
}
