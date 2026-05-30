import { Button, Card, DatePicker, Form, Input, Select } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { taskApi } from '../api/taskApi';
import { userApi } from '../api/userApi';
import { categoryLabel, priorityLabel } from '../utils/formatters';
import { useAuthStore } from '../store/authStore';
import { User } from '../types/models';

export function CreateTicketPage() {
  const nav = useNavigate();
  const role = useAuthStore((state) => state.role);
  const [form] = Form.useForm();
  const [users, setUsers] = useState<User[]>([]);

  useEffect(() => {
    userApi.users().then((loadedUsers) => {
      setUsers(loadedUsers);
      if (loadedUsers.length === 1) {
        form.setFieldValue('requesterId', loadedUsers[0].id);
      }
    }).catch(() => setUsers([]));
  }, [form]);

  const onFinish = async (values: any) => {
    const created = await taskApi.create({
      ...values,
      resolutionDeadline: values.resolutionDeadline?.toISOString()
    });
    nav(`/tickets/${created.id}`);
  };

  return <Card title='Создание обращения'>
    <Form form={form} layout='vertical' onFinish={onFinish}>
      <Form.Item name='title' label='Заголовок' rules={[{ required: true, message: 'Введите заголовок' }]}>
        <Input />
      </Form.Item>
      <Form.Item name='description' label='Описание' rules={[{ required: true, message: 'Введите описание' }]}>
        <Input.TextArea rows={5} />
      </Form.Item>
      <Form.Item name='requesterId' label='Автор обращения' rules={[{ required: true, message: 'Выберите автора' }]}>
        <Select
          showSearch
          disabled={role === 'USER'}
          placeholder='Выберите пользователя'
          optionFilterProp='label'
          options={users.map((user) => ({
            value: user.id,
            label: `${user.name || user.username} (${user.username})`
          }))}
        />
      </Form.Item>
      <Form.Item name='category' label='Категория'>
        <Select allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((value) => ({ value, label: categoryLabel[value as keyof typeof categoryLabel] }))} />
      </Form.Item>
      <Form.Item name='priority' label='Приоритет'>
        <Select allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((value) => ({ value, label: priorityLabel[value as keyof typeof priorityLabel] }))} />
      </Form.Item>
      <Form.Item name='resolutionDeadline' label='Срок решения'>
        <DatePicker showTime style={{ width: '100%' }} />
      </Form.Item>
      <Button type='primary' htmlType='submit'>Создать</Button>
    </Form>
  </Card>;
}
