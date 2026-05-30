import { Alert, Button, Card, Col, Descriptions, Form, Row, Select, Space, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { aiApi } from '../api/aiApi';
import { categoryLabel, priorityLabel, statusLabel } from '../utils/formatters';
import { taskApi } from '../api/taskApi';
import { useAuthStore } from '../store/authStore';
import { ClassifyResponse, RecommendResponse, SimilarResponse, Task } from '../types/models';

function prettyAiText(raw?: string) {
  if (!raw) return '';
  return raw
    .replace(/\r/g, '')
    .replace(/---/g, '\n')
    .replace(/#{3,4}\s*/g, '\n')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^\s*[-*]\s+/gm, '• ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

export function TicketDetailsPage() {
  const { id } = useParams();
  const ticketId = Number(id);
  const role = useAuthStore((state) => state.role);
  const canOperate = role === 'OPERATOR' || role === 'ADMIN';
  const [task, setTask] = useState<Task>();
  const [classify, setClassify] = useState<ClassifyResponse>();
  const [similar, setSimilar] = useState<SimilarResponse>();
  const [recommend, setRecommend] = useState<RecommendResponse>();
  const [aiForm] = Form.useForm();

  const text = useMemo(() => `${task?.title ?? ''}\n${task?.descriprion ?? ''}`.trim(), [task]);
  const load = async () => setTask(await taskApi.getById(ticketId));

  useEffect(() => { if (ticketId) load(); }, [ticketId]);
  if (!task) return <Card loading />;

  const runClassification = async () => {
    const result = await aiApi.classify(text);
    setClassify(result);
    aiForm.setFieldsValue({ category: result.category, priority: result.priority });
  };

  const applyAi = async (values: { category?: string; priority?: string }) => {
    if (!classify) return;
    const category = values.category ?? classify.category;
    const priority = values.priority ?? classify.priority;
    const updated = await taskApi.updateClassification(ticketId, category, priority);
    setTask(updated);
    message.success('Предложенная ИИ классификация сохранена в заявке.');
  };

  return <Row gutter={16}>
    <Col span={16}>
      <Card title={`Заявка #${task.id}`}>
        <Descriptions column={2}>
          <Descriptions.Item label='Тема'>{task.title}</Descriptions.Item>
          <Descriptions.Item label='Статус'>{statusLabel[task.status as keyof typeof statusLabel] ?? task.status}</Descriptions.Item>
          <Descriptions.Item label='Категория'>{task.category ? (categoryLabel[task.category as keyof typeof categoryLabel] ?? task.category) : '—'}</Descriptions.Item>
          <Descriptions.Item label='Приоритет'>{task.priority ? (priorityLabel[task.priority as keyof typeof priorityLabel] ?? task.priority) : '—'}</Descriptions.Item>
          <Descriptions.Item label='Автор'>{task.requester?.name || task.requester?.username || '—'}</Descriptions.Item>
          <Descriptions.Item label='Исполнитель'>{task.assignedTo?.name || task.assignedTo?.username || 'Не назначен'}</Descriptions.Item>
          <Descriptions.Item label='Срок'>{task.resolutionDeadline ?? '—'}</Descriptions.Item>
          <Descriptions.Item label='Описание' span={2}>{task.descriprion}</Descriptions.Item>
        </Descriptions>
        {canOperate && <Space wrap>
          <Button onClick={async () => setTask(await taskApi.changeStatus(ticketId, 'IN_PROGRESS', 'Взято в работу'))}>В работу</Button>
          <Button onClick={async () => setTask(await taskApi.escalate(ticketId, 'Эскалация оператором'))}>Эскалировать</Button>
          <Button onClick={async () => setTask(await taskApi.close(ticketId, 'Закрыто оператором'))}>Закрыть</Button>
        </Space>}
      </Card>
    </Col>
    <Col span={8}>
      <Card title='ИИ-помощник'>
        {!canOperate && <Alert type='info' showIcon message='ИИ-помощник доступен оператору и администратору.' />}
        {canOperate && <>
          <Space direction='vertical' style={{ width: '100%' }}>
            <Button block onClick={runClassification}>Получить классификацию</Button>
            <Button block onClick={async () => setSimilar(await aiApi.similar(text))}>Найти похожие обращения</Button>
            <Button block onClick={async () => setRecommend(await aiApi.recommend(text))}>Показать рекомендации</Button>
          </Space>

          {classify && <Card size='small' style={{ marginTop: 12 }}>
            <Typography.Text strong>Предложение ИИ</Typography.Text>
            <div>Категория: {categoryLabel[classify.category as keyof typeof categoryLabel] ?? classify.category}</div>
            <div>Приоритет: {priorityLabel[classify.priority as keyof typeof priorityLabel] ?? classify.priority}</div>
            <Typography.Paragraph>{classify.rationale}</Typography.Paragraph>
            <Form form={aiForm} onFinish={applyAi} layout='vertical'>
              <Form.Item name='category' label='Категория'>
                <Select allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((value) => ({ value, label: categoryLabel[value as keyof typeof categoryLabel] }))} />
              </Form.Item>
              <Form.Item name='priority' label='Приоритет'>
                <Select allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((value) => ({ value, label: priorityLabel[value as keyof typeof priorityLabel] }))} />
              </Form.Item>
              <Button htmlType='submit' type='primary'>Принять предложение ИИ</Button>
            </Form>
          </Card>}

          {similar && <Card size='small' title='Похожие обращения' style={{ marginTop: 12 }}>
            {(similar.similarTickets ?? []).map((item) => <div key={item.ticketId}>#{item.ticketId} {item.title} ({Math.round(item.score * 100)}%)</div>)}
            {(similar.resolvedCases ?? []).map((item) => <div key={`resolved-${item.ticketId}`}>#{item.ticketId} {item.title} ({item.fitPercent}%)</div>)}
          </Card>}

          {recommend && <Card size='small' style={{ marginTop: 12 }} title='Рекомендация'>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{prettyAiText(recommend.recommendation)}</Typography.Paragraph>
            {!!recommend.nextSteps?.length && <ul>{recommend.nextSteps.map((step, index) => <li key={index}>{step}</li>)}</ul>}
          </Card>}
        </>}
      </Card>
    </Col>
  </Row>;
}
