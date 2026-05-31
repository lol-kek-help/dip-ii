import { Button, Card, Col, DatePicker, Form, Input, Row, Table, Tag, Typography } from 'antd';
import { Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';
import { adminApi, AdminUser, AuditFilter, AuditLog, Dictionaries } from '../api/adminApi';

interface AuditFormValues { action?: string; entityType?: string; actorId?: string; createdRange?: [Dayjs, Dayjs]; }

export function AdminPage(){
  const [users,setUsers]=useState<AdminUser[]>([]);
  const [audit,setAudit]=useState<AuditLog[]>([]);
  const [auditTotal,setAuditTotal]=useState(0);
  const [dict,setDict]=useState<Dictionaries>();
  const [form] = Form.useForm<AuditFormValues>();

  const loadAudit = async (pageNumber = 0, pageSize = 10) => {
    const values = form.getFieldsValue();
    const filter: AuditFilter = { pageNumber, pageSize, action: values.action, entityType: values.entityType };
    if (values.actorId) filter.actorId = Number(values.actorId);
    if (values.createdRange) { filter.createdFrom = values.createdRange[0].toISOString(); filter.createdTo = values.createdRange[1].toISOString(); }
    const data = await adminApi.audit(filter);
    setAudit(data.items); setAuditTotal(data.totalElements);
  };

  useEffect(()=>{ adminApi.users().then(setUsers); adminApi.dictionaries().then(setDict); loadAudit(); },[]);
  return <Row gutter={[16,16]}><Col span={24}><Card title='Пользователи'><Table rowKey='id' dataSource={users} pagination={{pageSize:10}} columns={[{title:'ID',dataIndex:'id'},{title:'ФИО',dataIndex:'name'},{title:'Логин',dataIndex:'username'},{title:'Роль',dataIndex:'role',render:(r:string)=><Tag color={r==='ADMIN'?'red':r==='OPERATOR'?'blue':'green'}>{r}</Tag>}]} /></Card></Col>
  <Col span={24}><Card title='Справочники'><Typography.Text>Статусы: {dict?.statuses.join(', ')}</Typography.Text><br/><Typography.Text>Приоритеты: {dict?.priorities.join(', ')}</Typography.Text><br/><Typography.Text>Категории: {dict?.categories.join(', ')}</Typography.Text></Card></Col>
  <Col span={24}><Card title='Журнал аудита'>
    <Form form={form} layout='inline' onFinish={() => loadAudit(0, 10)} style={{ marginBottom: 16 }}>
      <Form.Item name='action'><Input placeholder='action' /></Form.Item>
      <Form.Item name='entityType'><Input placeholder='entityType' /></Form.Item>
      <Form.Item name='actorId'><Input placeholder='actorId' /></Form.Item>
      <Form.Item name='createdRange'><DatePicker.RangePicker showTime /></Form.Item>
      <Button htmlType='submit' type='primary'>Фильтровать</Button>
    </Form>
    <Table rowKey='id' dataSource={audit} pagination={{pageSize:10,total:auditTotal,onChange:(page,size)=>loadAudit(page-1,size)}} columns={[{title:'Дата',dataIndex:'createdAt'},{title:'Актор',render:(_,r:AuditLog)=>r.actor?.username ?? '—'},{title:'IP',dataIndex:'ipAddress'},{title:'Действие',dataIndex:'action'},{title:'Сущность',dataIndex:'entityType'},{title:'ID',dataIndex:'entityId'},{title:'Детали',dataIndex:'details'}]} />
  </Card></Col></Row>;
}
