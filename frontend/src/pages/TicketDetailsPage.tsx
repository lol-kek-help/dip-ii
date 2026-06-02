import { Alert, Button, Card, Col, Descriptions, Divider, Form, Input, List, Rate, Row, Select, Space, Switch, Tag, Timeline, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { aiApi } from '../api/aiApi';
import { categoryLabel, priorityLabel, statusLabel } from '../utils/formatters';
import { ticketApi } from '../api/ticketApi';
import { useAuthStore } from '../store/authStore';
import { Category, ClassifyResponse, Priority, RecommendResponse, SavedAiRecommendation, SimilarResponse, Ticket, TicketComment, TicketStatusHistory } from '../types/models';

function prettyAiText(raw?: string) {
  if (!raw) return '';
  return raw.replace(/\r/g, '').replace(/---/g, '\n').replace(/#{3,4}\s*/g, '\n').replace(/\*\*(.*?)\*\*/g, '$1').replace(/^\s*[-*]\s+/gm, '• ').replace(/\n{3,}/g, '\n\n').trim();
}

function ExplainabilityBlock({ explainability }: { explainability?: { mode?: string; sources?: string[]; llmStatus?: string; rawModelOutput?: string | null; fallbackReason?: string | null } }) {
  if (!explainability) return null;
  return <Alert style={{ marginTop: 8 }} type='info' showIcon message={`Режим: ${explainability.mode ?? '—'}, LLM: ${explainability.llmStatus ?? '—'}`} description={<>
    <div>Источники: {(explainability.sources ?? []).join(', ') || '—'}</div>
    {explainability.fallbackReason && <Alert style={{ marginTop: 8, marginBottom: 8 }} type='warning' showIcon message={explainability.fallbackReason} />}
    {explainability.rawModelOutput && <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }} ellipsis={{ rows: 4, expandable: true }}>Raw: {explainability.rawModelOutput}</Typography.Paragraph>}
  </>} />;
}

interface CommentFormValues { commentText: string; internalComment?: boolean; }
interface FeedbackFormValues { accepted: boolean; usefulnessScore?: number; feedbackComment?: string; }

export function TicketDetailsPage() {
  const { id } = useParams();
  const ticketId = Number(id);
  const role = useAuthStore((state) => state.role);
  const canOperate = role === 'OPERATOR' || role === 'ADMIN';
  const [ticket, setTicket] = useState<Ticket>();
  const [comments, setComments] = useState<TicketComment[]>([]);
  const [history, setHistory] = useState<TicketStatusHistory[]>([]);
  const [classify, setClassify] = useState<ClassifyResponse>();
  const [similar, setSimilar] = useState<SimilarResponse>();
  const [recommend, setRecommend] = useState<RecommendResponse>();
  const [savedRecommendations, setSavedRecommendations] = useState<SavedAiRecommendation[]>([]);
  const [aiForm] = Form.useForm();
  const [commentForm] = Form.useForm<CommentFormValues>();
  const [closeForm] = Form.useForm<{ resolutionComment: string }>();
  const [escalateForm] = Form.useForm<{ reason: string }>();
  const [feedbackForm] = Form.useForm<FeedbackFormValues>();

  const text = useMemo(() => `${ticket?.title ?? ''}\n${ticket?.description ?? ''}`.trim(), [ticket]);
  const load = async () => {
    const [ticketData, commentsData, historyData, recommendationsData] = await Promise.all([
      ticketApi.getById(ticketId), ticketApi.comments(ticketId), ticketApi.statusHistory(ticketId),
      ticketApi.aiRecommendations(ticketId)
    ]);
    setTicket(ticketData); setComments(commentsData);
    setHistory(historyData); setSavedRecommendations(recommendationsData);
  };

  useEffect(() => { if (ticketId) load(); }, [ticketId]);
  if (!ticket) return <Card loading />;

  const runClassification = async () => {
    const result = await aiApi.classify(text);
    setClassify(result);
    aiForm.setFieldsValue({ category: result.category, priority: result.priority });
  };

  const applyAi = async (values: { category?: Category; priority?: Priority }) => {
    if (!classify) return;
    const updated = await ticketApi.updateClassification(ticketId, values.category ?? classify.category, values.priority ?? classify.priority);
    setTicket(updated);
    message.success('Предложенная ИИ классификация сохранена в обращении.');
  };

  const addComment = async (values: CommentFormValues) => {
    await ticketApi.addComment(ticketId, { commentText: values.commentText, internalComment: Boolean(values.internalComment) });
    commentForm.resetFields();
    setComments(await ticketApi.comments(ticketId));
  };

  const saveAiRecommendation = async () => {
    const saved = await ticketApi.saveAiRecommendation(ticketId);
    setSavedRecommendations([saved, ...savedRecommendations]);
    message.success('AI-рекомендация сохранена в обращении.');
  };

  return <Row gutter={[16,16]}>
    <Col span={16}>
      <Card title={`Обращение #${ticket.id}`}>
        <Descriptions column={2}>
          <Descriptions.Item label='Тема'>{ticket.title}</Descriptions.Item>
          <Descriptions.Item label='Статус'>{statusLabel[ticket.status] ?? ticket.status}</Descriptions.Item>
          <Descriptions.Item label='Категория'>{ticket.category ? (categoryLabel[ticket.category] ?? ticket.category) : '—'}</Descriptions.Item>
          <Descriptions.Item label='Приоритет'>{ticket.priority ? (priorityLabel[ticket.priority] ?? ticket.priority) : '—'}</Descriptions.Item>
          <Descriptions.Item label='Автор'>{ticket.requester?.name || ticket.requester?.username || '—'}</Descriptions.Item>
          <Descriptions.Item label='Исполнитель'>{ticket.assignedTo?.name || ticket.assignedTo?.username || 'Не назначен'}</Descriptions.Item>
          <Descriptions.Item label='Срок'>{ticket.resolutionDeadline ?? '—'}</Descriptions.Item>
          <Descriptions.Item label='Описание' span={2}>{ticket.description}</Descriptions.Item>
          <Descriptions.Item label='Решение' span={2}>{ticket.resolutionComment || '—'}</Descriptions.Item>
        </Descriptions>
        {canOperate && <>
          <Space wrap style={{ marginBottom: 12 }}>
            <Button onClick={async () => setTicket(await ticketApi.changeStatus(ticketId, 'IN_PROGRESS', 'Взято в работу'))}>В работу</Button>
          </Space>
          <Form form={escalateForm} layout='inline' onFinish={async (v) => setTicket(await ticketApi.escalate(ticketId, v.reason))}>
            <Form.Item name='reason' rules={[{ required: true, message: 'Укажите комментарий эскалации' }]}><Input placeholder='Комментарий для эскалации' /></Form.Item>
            <Button htmlType='submit'>Эскалировать</Button>
          </Form>
          <Form form={closeForm} layout='inline' style={{ marginTop: 8 }} onFinish={async (v) => setTicket(await ticketApi.close(ticketId, v.resolutionComment))}>
            <Form.Item name='resolutionComment' rules={[{ required: true, message: 'Укажите комментарий решения' }]}><Input placeholder='Комментарий закрытия' /></Form.Item>
            <Button htmlType='submit' type='primary'>Закрыть</Button>
          </Form>
        </>}
      </Card>

      <Card title='Комментарии' style={{ marginTop: 16 }}>
        <List dataSource={comments} locale={{ emptyText: 'Комментариев пока нет' }} renderItem={(comment) => <List.Item>
          <List.Item.Meta title={<Space>{comment.author.name || comment.author.username}{comment.internalComment && <Tag color='orange'>Внутренний</Tag>}</Space>} description={<><div style={{ whiteSpace: 'pre-wrap' }}>{comment.commentText}</div><Typography.Text type='secondary'>{comment.createdAt}</Typography.Text></>} />
        </List.Item>} />
        <Divider />
        <Form form={commentForm} layout='vertical' onFinish={addComment}>
          <Form.Item name='commentText' label='Новый комментарий' rules={[{ required: true, message: 'Введите комментарий' }]}><Input.TextArea rows={3} /></Form.Item>
          {canOperate && <Form.Item name='internalComment' label='Внутренний комментарий' valuePropName='checked'><Switch /></Form.Item>}
          <Button htmlType='submit' type='primary'>Добавить комментарий</Button>
        </Form>
      </Card>

      <Card title='История статусов' style={{ marginTop: 16 }}>
        <Timeline items={history.map((h) => ({ children: <><b>{h.fromStatus ?? '—'} → {h.toStatus}</b><div>{h.reason || 'Без комментария'}</div><Typography.Text type='secondary'>{h.createdAt} · {h.changedBy?.username ?? 'система'}</Typography.Text></> }))} />
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
            <Button block type='primary' onClick={saveAiRecommendation}>Сгенерировать и сохранить рекомендацию</Button>
          </Space>

          {classify && <Card size='small' style={{ marginTop: 12 }}>
            <Typography.Text strong>Предложение ИИ</Typography.Text>
            <div>Категория: {categoryLabel[classify.category] ?? classify.category}</div>
            <div>Приоритет: {priorityLabel[classify.priority] ?? classify.priority}</div>
            <Typography.Paragraph>{classify.rationale}</Typography.Paragraph>
            <ExplainabilityBlock explainability={classify.explainability} />
            <Form form={aiForm} onFinish={applyAi} layout='vertical'>
              <Form.Item name='category' label='Категория'><Select allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((value) => ({ value, label: categoryLabel[value as keyof typeof categoryLabel] }))} /></Form.Item>
              <Form.Item name='priority' label='Приоритет'><Select allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((value) => ({ value, label: priorityLabel[value as keyof typeof priorityLabel] }))} /></Form.Item>
              <Button htmlType='submit' type='primary'>Принять предложение ИИ</Button>
            </Form>
          </Card>}

          {similar && <Card size='small' title='Похожие обращения и источники' style={{ marginTop: 12 }}>
            <Typography.Text strong>Похожие обращения</Typography.Text>
            {(similar.tickets ?? []).map((item) =>
                <div key={item.ticketId}>#{item.ticketId} {item.title} ({Math.round(item.score * 100)}%)</div>)}
            <Divider />
            <Typography.Text strong>Решённые кейсы</Typography.Text>
            {(similar.resolvedCases ?? []).map((item) =>
                <div key={`resolved-${item.ticketId}`}>#{item.ticketId} {item.title} ({item.fitPercent}%) — {item.resolutionComment}</div>)}
            <Divider />
            <Typography.Text strong>Статьи базы знаний</Typography.Text>
            {(similar.articles ?? []).map((article) => <div key={article}>{article}</div>)}
            <ExplainabilityBlock explainability={similar.explainability} />
          </Card>}

          {recommend && <Card size='small' style={{ marginTop: 12 }} title='Рекомендация'>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{prettyAiText(recommend.recommendation)}</Typography.Paragraph>
            {!!recommend.steps?.length && <ul>{recommend.steps.map((step, index) => <li key={index}>{step}</li>)}</ul>}
            <ExplainabilityBlock explainability={recommend.explainability} />
          </Card>}

          <Card size='small' style={{ marginTop: 12 }} title='Сохранённые AI-рекомендации'>
            <List dataSource={savedRecommendations} locale={{ emptyText: 'Нет сохранённых рекомендаций' }} renderItem={
              (item) => <List.Item>
              <Space direction='vertical' style={{ width: '100%' }}>
                <Typography.Paragraph ellipsis={{ rows: 4, expandable: true }}>{prettyAiText(item.recommendation)}</Typography.Paragraph>
                <ExplainabilityBlock explainability={{ mode: item.mode, sources: item.sources, llmStatus: item.llmStatus,
                  rawModelOutput: item.rawModelOutput, fallbackReason: item.fallbackReason }} />
                <Form form={feedbackForm} layout='inline' onFinish={async (values) => {
                  const updated = await ticketApi.evaluateAiRecommendation(ticketId, item.id, {
                    accepted: Boolean(values.accepted), usefulnessScore: values.usefulnessScore,
                    feedbackComment: values.feedbackComment });
                  setSavedRecommendations(savedRecommendations.map((r) =>
                      r.id === item.id ? updated : r));
                }}>
                  <Form.Item name='accepted' label='Принята' valuePropName='checked'><Switch /></Form.Item>
                  <Form.Item name='usefulnessScore' label='Оценка'><Rate /></Form.Item>
                  <Form.Item name='feedbackComment'><Input placeholder='Комментарий качества' /></Form.Item>
                  <Button htmlType='submit'>Оценить</Button>
                </Form>
              </Space>
            </List.Item>} />
          </Card>
        </>}
      </Card>
    </Col>
  </Row>;
}
