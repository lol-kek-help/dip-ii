import { Card, Col, Row, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { adminApi, AdminUser, AuditLog, Dictionaries } from '../api/adminApi';

export function AdminPage(){
  const [users,setUsers]=useState<AdminUser[]>([]); const [audit,setAudit]=useState<AuditLog[]>([]); const [dict,setDict]=useState<Dictionaries>();
  useEffect(()=>{ adminApi.users().then(setUsers); adminApi.audit(200).then(setAudit); adminApi.dictionaries().then(setDict); },[]);
  return <Row gutter={[16,16]}><Col span={24}><Card title='Пользователи'><Table rowKey='id' dataSource={users} pagination={{pageSize:10}} columns={[{title:'ID',dataIndex:'id'},{title:'ФИО',dataIndex:'name'},{title:'Логин',dataIndex:'username'},{title:'Роль',dataIndex:'role',render:(r)=><Tag color={r==='ADMIN'?'red':r==='OPERATOR'?'blue':'green'}>{r}</Tag>}]} /></Card></Col>
  <Col span={24}><Card title='Справочники'><Typography.Text>Статусы: {dict?.statuses.join(', ')}</Typography.Text><br/><Typography.Text>Приоритеты: {dict?.priorities.join(', ')}</Typography.Text><br/><Typography.Text>Категории: {dict?.categories.join(', ')}</Typography.Text></Card></Col>
  <Col span={24}><Card title='Журнал аудита'><Table rowKey='id' dataSource={audit} pagination={{pageSize:10}} columns={[{title:'Дата',dataIndex:'createdAt'},{title:'Действие',dataIndex:'action'},{title:'Сущность',dataIndex:'entityType'},{title:'ID',dataIndex:'entityId'},{title:'Детали',dataIndex:'details'}]} /></Card></Col></Row>;
}
