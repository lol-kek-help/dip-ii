import { Alert, Button, Card, Col, Descriptions, Form, Input, Row, Select, Space, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { aiApi } from '../api/aiApi';
import { taskApi } from '../api/taskApi';
import { Task } from '../types/models';

export function TicketDetailsPage() {
  const { id } = useParams(); const ticketId = Number(id);
  const [task, setTask] = useState<Task>(); const [classify, setClassify] = useState<any>(); const [similar, setSimilar] = useState<any>(); const [recommend, setRecommend] = useState<any>();
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

  return <Row gutter={16}><Col span={16}><Card title={`Заявка #${task.id}`}><Descriptions column={2}><Descriptions.Item label='Тема'>{task.title}</Descriptions.Item><Descriptions.Item label='Статус'>{task.status}</Descriptions.Item><Descriptions.Item label='Категория'>{task.category}</Descriptions.Item><Descriptions.Item label='Приоритет'>{task.priority}</Descriptions.Item><Descriptions.Item label='Исполнитель'>{task.assignedTo?.username ?? 'Не назначен'}</Descriptions.Item><Descriptions.Item label='Срок'>{task.resolutionDeadline ?? '—'}</Descriptions.Item><Descriptions.Item label='Описание' span={2}>{task.descriprion}</Descriptions.Item></Descriptions>
  <Space wrap>
    <Button onClick={async()=>setTask(await taskApi.changeStatus(ticketId,'IN_PROGRESS','Взято в работу'))}>В работу</Button>
    <Button onClick={async()=>setTask(await taskApi.escalate(ticketId,'Эскалация оператором'))}>Эскалировать</Button>
    <Button onClick={async()=>setTask(await taskApi.close(ticketId,'Закрыто оператором'))}>Закрыть</Button>
  </Space></Card></Col>
  <Col span={8}><Card title='ИИ-помощник'><Space direction='vertical' style={{ width: '100%' }}><Button block onClick={async()=>setClassify(await aiApi.classify(text))}>Получить классификацию</Button><Button block onClick={async()=>setSimilar(await aiApi.similar(text))}>Найти похожие обращения</Button><Button block onClick={async()=>setRecommend(await aiApi.recommend(text))}>Показать рекомендации</Button></Space>
  {classify && <Card size='small' style={{ marginTop: 12 }}><Typography.Text strong>Предложение ИИ</Typography.Text><div>Категория: {classify.category}</div><div>Приоритет: {classify.priority}</div><div>{classify.rationale}</div><Form onFinish={applyAi} layout='vertical'><Form.Item name='category' label='Категория'><Select allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((v)=>({value:v}))} /></Form.Item><Form.Item name='priority' label='Приоритет'><Select allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((v)=>({value:v}))} /></Form.Item><Button htmlType='submit' type='primary'>Принять предложение ИИ</Button></Form></Card>}
  {similar && <Card size='small' title='Похожие' style={{ marginTop: 12 }}>{similar.tickets?.map((t:any)=><div key={t.ticketId}>#{t.ticketId} {t.title} ({Math.round(t.score*100)}%)</div>)}</Card>}
  {recommend && <Alert style={{ marginTop: 12 }} type='info' message='Рекомендация' description={recommend.recommendation} />}
  </Card></Col></Row>;
}
