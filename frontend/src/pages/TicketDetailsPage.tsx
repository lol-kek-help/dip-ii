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
  return <Alert style={{ marginTop: 12 }} type='info' showIcon message={`Режим: ${explainability.mode ?? '—'}, LLM: ${explainability.llmStatus ?? '—'}`} description={<>
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
  const [aiLoading, setAiLoading] = useState<string>();
  const [aiForm] = Form.useForm();
  const [commentForm] = Form.useForm<CommentFormValues>();
  const [closeForm] = Form.useForm<{ resolutionComment: string }>();
  const [escalateForm] = Form.useForm<{ reason: string }>();

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
    setAiLoading('classify');
    try {
      const result = await aiApi.classify(text);
      setClassify(result);
      aiForm.setFieldsValue({ category: result.category, priority: result.priority });
    } finally {
      setAiLoading(undefined);
    }
  };

  const loadSources = async () => {
    setAiLoading('sources');
    try {
      setSimilar(await aiApi.similar(text));
    } finally {
      setAiLoading(undefined);
    }
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
    setAiLoading('recommend');
    try {
      const saved = await ticketApi.saveAiRecommendation(ticketId);
      setSavedRecommendations([saved, ...savedRecommendations]);
      setRecommend({
        recommendation: saved.recommendation,
        steps: saved.steps,
        explainability: {
          mode: saved.mode ?? 'RAG_GROUNDED',
          sources: saved.sources ?? [],
          llmStatus: saved.llmStatus ?? 'OK',
          rawModelOutput: saved.rawModelOutput,
          fallbackReason: saved.fallbackReason
        }
      });
      setSimilar(await aiApi.similar(text));
      message.success('RAG-рекомендация сгенерирована по базе знаний/похожим случаям и сохранена.');
    } finally {
      setAiLoading(undefined);
    }
  };

  const renderSources = () => <Row gutter={[16, 16]}>
    <Col xs={24} lg={8}>
      <Card size='small' title='Похожие обращения' style={{ height: '100%' }}>
        <List size='small' dataSource={similar?.tickets ?? []} locale={{ emptyText: 'Пока не загружено' }} renderItem={(item) =>
          <List.Item>#{item.ticketId} {item.title} <Tag>{Math.round(item.score * 100)}%</Tag></List.Item>
        } />
      </Card>
    </Col>
    <Col xs={24} lg={8}>
      <Card size='small' title='Решённые инциденты' style={{ height: '100%' }}>
        <List size='small' dataSource={similar?.resolvedCases ?? []} locale={{ emptyText: 'Релевантные кейсы не найдены' }} renderItem={(item) =>
          <List.Item>
            <Space direction='vertical' size={2} style={{ width: '100%' }}>
              <Typography.Text strong>#{item.ticketId} {item.title} <Tag color='green'>{item.fitPercent}%</Tag></Typography.Text>
              <Typography.Text>{item.resolutionComment || 'Решение не заполнено'}</Typography.Text>
            </Space>
          </List.Item>
        } />
      </Card>
    </Col>
    <Col xs={24} lg={8}>
      <Card size='small' title='Статьи базы знаний' style={{ height: '100%' }}>
        <List size='small' dataSource={similar?.articles ?? []} locale={{ emptyText: 'Релевантные статьи не найдены' }} renderItem={(article) =>
          <List.Item>
            <Space direction='vertical' size={2} style={{ width: '100%' }}>
              <Typography.Text strong>{article.title} <Tag color='blue'>{article.fitPercent}%</Tag></Typography.Text>
              <Typography.Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 3, expandable: true }}>{article.content}</Typography.Paragraph>
            </Space>
          </List.Item>
        } />
      </Card>
    </Col>
  </Row>;

  return <Space direction='vertical' size={16} style={{ width: '100%' }}>
    <Row gutter={[16, 16]} align='top'>
      <Col xs={24} xl={16}>
        <Card title={`Обращение #${ticket.id}`}>
          <Descriptions column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label='Тема'>{ticket.title}</Descriptions.Item>
            <Descriptions.Item label='Статус'>{statusLabel[ticket.status] ?? ticket.status}</Descriptions.Item>
            <Descriptions.Item label='Категория'>{ticket.category ? (categoryLabel[ticket.category] ?? ticket.category) : '—'}</Descriptions.Item>
            <Descriptions.Item label='Приоритет'>{ticket.priority ? (priorityLabel[ticket.priority] ?? ticket.priority) : '—'}</Descriptions.Item>
            <Descriptions.Item label='Автор'>{ticket.requester?.name || ticket.requester?.username || '—'}</Descriptions.Item>
            <Descriptions.Item label='Исполнитель'>{ticket.assignedTo?.name || ticket.assignedTo?.username || 'Не назначен'}</Descriptions.Item>
            <Descriptions.Item label='Срок'>{ticket.resolutionDeadline ?? '—'}</Descriptions.Item>
            <Descriptions.Item label='Описание' span={2}><Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{ticket.description}</Typography.Paragraph></Descriptions.Item>
            <Descriptions.Item label='Решение' span={2}>{ticket.resolutionComment || '—'}</Descriptions.Item>
          </Descriptions>
          {canOperate && <>
            <Divider />
            <Space wrap style={{ marginBottom: 12 }}>
              <Button onClick={async () => setTicket(await ticketApi.changeStatus(ticketId, 'IN_PROGRESS', 'Взято в работу'))}>В работу</Button>
            </Space>
            <Space direction='vertical' style={{ width: '100%' }}>
              <Form form={escalateForm} layout='inline' onFinish={async (v) => setTicket(await ticketApi.escalate(ticketId, v.reason))}>
                <Form.Item name='reason' rules={[{ required: true, message: 'Укажите комментарий эскалации' }]}><Input placeholder='Комментарий для эскалации' /></Form.Item>
                <Button htmlType='submit'>Эскалировать</Button>
              </Form>
              <Form form={closeForm} layout='inline' onFinish={async (v) => setTicket(await ticketApi.close(ticketId, v.resolutionComment))}>
                <Form.Item name='resolutionComment' rules={[{ required: true, message: 'Укажите комментарий решения' }]}><Input placeholder='Комментарий закрытия' /></Form.Item>
                <Button htmlType='submit' type='primary'>Закрыть</Button>
              </Form>
            </Space>
          </>}
        </Card>
      </Col>

      <Col xs={24} xl={8}>
        <Space direction='vertical' size={16} style={{ width: '100%' }}>
          <Card title='Комментарии'>
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

          <Card title='История статусов'>
            <Timeline items={history.map((h) => ({ children: <><b>{h.fromStatus ?? '—'} → {h.toStatus}</b><div>{h.reason || 'Без комментария'}</div><Typography.Text type='secondary'>{h.createdAt} · {h.changedBy?.username ?? 'система'}</Typography.Text></> }))} />
          </Card>
        </Space>
      </Col>
    </Row>

    <Card title='ИИ-помощник' extra={<Tag color='purple'>RAG: база знаний + решённые инциденты</Tag>}>
      {!canOperate && <Alert type='info' showIcon message='ИИ-помощник доступен оператору и администратору.' />}
      {canOperate && <Space direction='vertical' size={16} style={{ width: '100%' }}>
        <Alert showIcon type='info' message='Рекомендация строится в первую очередь по релевантным статьям базы знаний и похожим решённым обращениям. LLM используется только как слой формулировки и не должен подменять найденные источники.' />
        <Space wrap>
          <Button onClick={runClassification} loading={aiLoading === 'classify'}>Получить классификацию</Button>
          <Button onClick={loadSources} loading={aiLoading === 'sources'}>Обновить источники RAG</Button>
          <Button type='primary' onClick={saveAiRecommendation} loading={aiLoading === 'recommend'}>Сгенерировать и сохранить рекомендацию</Button>
        </Space>

        {classify && <Card size='small' title='Предложение классификации'>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={10}>
              <div>Категория: {categoryLabel[classify.category] ?? classify.category}</div>
              <div>Приоритет: {priorityLabel[classify.priority] ?? classify.priority}</div>
              <Typography.Paragraph>{classify.rationale}</Typography.Paragraph>
            </Col>
            <Col xs={24} md={14}>
              <Form form={aiForm} onFinish={applyAi} layout='inline'>
                <Form.Item name='category' label='Категория'><Select style={{ width: 160 }} allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((value) => ({ value, label: categoryLabel[value as keyof typeof categoryLabel] }))} /></Form.Item>
                <Form.Item name='priority' label='Приоритет'><Select style={{ width: 160 }} allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((value) => ({ value, label: priorityLabel[value as keyof typeof priorityLabel] }))} /></Form.Item>
                <Button htmlType='submit' type='primary'>Принять</Button>
              </Form>
            </Col>
          </Row>
          <ExplainabilityBlock explainability={classify.explainability} />
        </Card>}

        {similar && <Card size='small' title='Источники для рекомендации'>
          {renderSources()}
          <ExplainabilityBlock explainability={similar.explainability} />
        </Card>}

        {recommend && <Card size='small' title='Последняя сгенерированная рекомендация'>
          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{prettyAiText(recommend.recommendation)}</Typography.Paragraph>
          {!!recommend.steps?.length && <><Divider /><Typography.Text strong>Первые действия из источников</Typography.Text><ol>{recommend.steps.map((step, index) => <li key={index}>{step}</li>)}</ol></>}
          <ExplainabilityBlock explainability={recommend.explainability} />
        </Card>}

        <Card size='small' title='Сохранённые AI-рекомендации'>
          <List dataSource={savedRecommendations} locale={{ emptyText: 'Нет сохранённых рекомендаций' }} renderItem={
            (item) => <SavedRecommendationItem item={item} ticketId={ticketId} onUpdated={(updated) => setSavedRecommendations(savedRecommendations.map((r) => r.id === item.id ? updated : r))} />
          } />
        </Card>
      </Space>}
    </Card>
  </Space>;
}

function SavedRecommendationItem({ item, ticketId, onUpdated }: { item: SavedAiRecommendation; ticketId: number; onUpdated: (value: SavedAiRecommendation) => void }) {
  const [form] = Form.useForm<FeedbackFormValues>();
  return <List.Item>
    <Space direction='vertical' style={{ width: '100%' }}>
      <Typography.Paragraph ellipsis={{ rows: 4, expandable: true }}>{prettyAiText(item.recommendation)}</Typography.Paragraph>
      <ExplainabilityBlock explainability={{ mode: item.mode, sources: item.sources, llmStatus: item.llmStatus,
        rawModelOutput: item.rawModelOutput, fallbackReason: item.fallbackReason }} />
      <Form form={form} layout='inline' onFinish={async (values) => {
        const updated = await ticketApi.evaluateAiRecommendation(ticketId, item.id, {
          accepted: Boolean(values.accepted), usefulnessScore: values.usefulnessScore,
          feedbackComment: values.feedbackComment });
        onUpdated(updated);
      }}>
        <Form.Item name='accepted' label='Принята' valuePropName='checked'><Switch /></Form.Item>
        <Form.Item name='usefulnessScore' label='Оценка'><Rate /></Form.Item>
        <Form.Item name='feedbackComment'><Input placeholder='Комментарий качества' /></Form.Item>
        <Button htmlType='submit'>Оценить</Button>
      </Form>
    </Space>
  </List.Item>;
}
