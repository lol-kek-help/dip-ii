import { Alert, Button, Card, Col, Descriptions, Form, Row, Select, Space, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { aiApi } from '../api/aiApi';
import { categoryLabel, priorityLabel, statusLabel } from '../utils/formatters';
import { taskApi } from '../api/taskApi';
import { Task } from '../types/models';


function prettyAiText(raw?: string) {
  if (!raw) return '';
  return raw
    .replace(/\r/g, '')
    .replace(/---/g, '\n')
    .replace(/###\s*/g, '')
    .replace(/####\s*/g, '\n')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/^\s*\*\s+/gm, '• ')
    .replace(/
{3,}/g, '\n\n')
    .trim();
}

export function TicketDetailsPage() {
  const { id } = useParams(); const ticketId = Number(id);
  const [task, setTask] = useState<Task>(); const [classify, setClassify] = useState<any>(); const [similar, setSimilar] = useState<any>(); const [recommend, setRecommend] = useState<any>();
  const [aiForm] = Form.useForm();
  const text = useMemo(() => `${task?.title ?? ''} ${task?.descriprion ?? ''}`.trim(), [task]);
  const load = async () => setTask(await taskApi.getById(ticketId));
  useEffect(() => { if (ticketId) load(); }, [ticketId]);
  if (!task) return <Card loading />;

  const applyAi = async (values?: any) => {
    if (!classify) return;
    await taskApi.changeStatus(ticketId, task.status, 'Применение AI-классификации');
    setTask({ ...task, category: values?.category ?? classify.category, priority: values?.priority ?? classify.priority });
    message.success('Предложение ИИ применено локально к карточке. Для персистентного изменения добавьте backend endpoint обновления поля категории/приоритета.');
  };

  return <Row gutter={16}><Col span={16}><Card title={`Заявка #${task.id}`}><Descriptions column={2}><Descriptions.Item label='Тема'>{task.title}</Descriptions.Item><Descriptions.Item label='Статус'>{statusLabel[task.status as keyof typeof statusLabel] ?? task.status}</Descriptions.Item><Descriptions.Item label='Категория'>{task.category ? (categoryLabel[task.category as keyof typeof categoryLabel] ?? task.category) : '—'}</Descriptions.Item><Descriptions.Item label='Приоритет'>{task.priority ? (priorityLabel[task.priority as keyof typeof priorityLabel] ?? task.priority) : '—'}</Descriptions.Item><Descriptions.Item label='Исполнитель'>{task.assignedTo?.username ?? 'Не назначен'}</Descriptions.Item><Descriptions.Item label='Срок'>{task.resolutionDeadline ?? '—'}</Descriptions.Item><Descriptions.Item label='Описание' span={2}>{task.descriprion}</Descriptions.Item></Descriptions>
  <Space wrap>
    <Button onClick={async()=>setTask(await taskApi.changeStatus(ticketId,'IN_PROGRESS','Взято в работу'))}>В работу</Button>
    <Button onClick={async()=>setTask(await taskApi.escalate(ticketId,'Эскалация оператором'))}>Эскалировать</Button>
    <Button onClick={async()=>setTask(await taskApi.close(ticketId,'Закрыто оператором'))}>Закрыть</Button>
  </Space></Card></Col>
  <Col span={8}><Card title='ИИ-помощник'><Space direction='vertical' style={{ width: '100%' }}><Button block onClick={async()=>{ const c = await aiApi.classify(text); setClassify(c); aiForm.setFieldsValue({ category: c.category, priority: c.priority }); }}>Получить классификацию</Button><Button block onClick={async()=>setSimilar(await aiApi.similar(text))}>Найти похожие обращения</Button><Button block onClick={async()=>setRecommend(await aiApi.recommend(text))}>Показать рекомендации</Button></Space>
  {classify && <Card size='small' style={{ marginTop: 12 }}><Typography.Text strong>Предложение ИИ</Typography.Text><div>Категория: {categoryLabel[classify.category as keyof typeof categoryLabel] ?? classify.category}</div><div>Приоритет: {priorityLabel[classify.priority as keyof typeof priorityLabel] ?? classify.priority}</div><div>{classify.rationale}</div><Form form={aiForm} onFinish={applyAi} layout='vertical'><Form.Item name='category' label='Категория'><Select allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((v)=>({value:v,label:categoryLabel[v as keyof typeof categoryLabel]}))} /></Form.Item><Form.Item name='priority' label='Приоритет'><Select allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((v)=>({value:v,label:priorityLabel[v as keyof typeof priorityLabel]}))} /></Form.Item><Button htmlType='submit' type='primary'>Принять предложение ИИ</Button></Form></Card>}
  {similar && <Card size='small' title='Похожие' style={{ marginTop: 12 }}>{similar.tickets?.map((t:any)=><div key={t.ticketId}>#{t.ticketId} {t.title} ({Math.round(t.score*100)}%)</div>)}</Card>}
  {recommend && <Card size='small' style={{ marginTop: 12 }} title='Рекомендация'><Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{prettyAiText(recommend.recommendation)}</Typography.Paragraph></Card>}
  </Card></Col></Row>;
}
