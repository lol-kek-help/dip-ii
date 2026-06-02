import { Alert, Badge, Button, Card, Checkbox, Col, Collapse, Descriptions, Divider, Drawer, Empty, Form, Input, List, Modal, Progress, Radio, Rate, Row, Select, Space, Switch, Tabs, Tag, Timeline, Typography, message } from 'antd';
import { CheckCircleOutlined, HistoryOutlined, RobotOutlined, SafetyOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { aiApi } from '../api/aiApi';
import { categoryLabel, priorityColor, priorityLabel, statusColor, statusLabel } from '../utils/formatters';
import { ticketApi } from '../api/ticketApi';
import { useAuthStore } from '../store/authStore';
import { Category, ClassifyResponse, Explainability, Priority, RecommendResponse, RecommendationMode, SavedAiRecommendation, SimilarResponse, Ticket, TicketComment, TicketStatusHistory } from '../types/models';

const recommendationModes: { value: RecommendationMode; label: string; description: string }[] = [
  { value: 'SHORT', label: 'Краткая рекомендация', description: 'Сжатый вывод и 1–2 первых действия.' },
  { value: 'STEP_BY_STEP', label: 'Пошаговое решение', description: 'Последовательный план для оператора.' },
  { value: 'USER_REPLY', label: 'Ответ пользователю', description: 'Готовый текст в понятном пользователю стиле.' },
  { value: 'INTERNAL_COMMENT', label: 'Внутренний комментарий', description: 'Служебная заметка без лишних объяснений.' },
  { value: 'TECHNICAL_GUIDE', label: 'Техническая инструкция', description: 'Проверки и действия для технического специалиста.' },
  { value: 'ESCALATION_SUMMARY', label: 'Резюме для эскалации', description: 'Короткое описание для передачи другой группе.' }
];

function prettyAiText(raw?: string) {
  if (!raw) return '';
  return raw.replace(/\r/g, '').replace(/---/g, '\n').replace(/\*\*(.*?)\*\*/g, '$1').replace(/\n{3,}/g, '\n\n').trim();
}

function renderFormattedAiText(raw?: string) {
  const text = prettyAiText(raw);
  if (!text) return <Empty description='Черновик пока не сгенерирован' />;
  const blocks = text.split(/\n{2,}/).map((block) => block.trim()).filter(Boolean);
  return <Space direction='vertical' size={12} style={{ width: '100%' }}>
    {blocks.map((block, index) => {
      const lines = block.split('\n').map((line) => line.trim()).filter(Boolean);
      const first = lines[0] ?? '';
      const heading = first.replace(/^#{1,6}\s*/, '');
      if (/^#{1,6}\s+/.test(first)) {
        const rest = lines.slice(1);
        return <Card key={index} size='small' className='ai-output-section' title={heading}>
          {rest.length ? renderFormattedAiText(rest.join('\n')) : null}
        </Card>;
      }
      if (lines.every((line) => /^[-•]\s+/.test(line))) {
        return <ul key={index} className='ai-output-list'>{lines.map((line, itemIndex) => <li key={itemIndex}>{line.replace(/^[-•]\s+/, '')}</li>)}</ul>;
      }
      if (lines.every((line) => /^\d+[.)]\s+/.test(line))) {
        return <ol key={index} className='ai-output-list'>{lines.map((line, itemIndex) => <li key={itemIndex}>{line.replace(/^\d+[.)]\s+/, '')}</li>)}</ol>;
      }
      return <Typography.Paragraph key={index} className='ai-output-paragraph'>{block.replace(/^[-•]\s+/, '')}</Typography.Paragraph>;
    })}
  </Space>;
}

function ExplainabilityBlock({ explainability }: { explainability?: Partial<Explainability> }) {
  if (!explainability) return null;
  const llmOk = explainability.llmStatus === 'OK';
  return <Collapse ghost size='small' style={{ marginTop: 8 }} items={[{
    key: 'explainability',
    label: <Space><SafetyOutlined />Технические детали AI <Tag color={llmOk ? 'green' : 'orange'}>{explainability.llmStatus ?? '—'}</Tag></Space>,
    children: <Space direction='vertical' size={8} style={{ width: '100%' }}>
      <div><Typography.Text strong>Режим:</Typography.Text> {explainability.mode ?? '—'}</div>
      <div><Typography.Text strong>Источники:</Typography.Text> {(explainability.sources ?? []).join(', ') || '—'}</div>
      {explainability.fallbackReason && <Alert type='warning' showIcon message={explainability.fallbackReason} />}
      {explainability.rawModelOutput && <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }} ellipsis={{ rows: 6, expandable: true }}>Raw: {explainability.rawModelOutput}</Typography.Paragraph>}
    </Space>
  }]} />;
}

interface CommentFormValues { commentText: string; internalComment?: boolean; }
interface FeedbackFormValues { accepted: boolean; usefulnessScore?: number; feedbackComment?: string; }

type SourceItem = { key: string; type: 'article' | 'case' | 'ticket'; title: string; fit: number; description: string; meta: string; color: string };

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
  const [draft, setDraft] = useState<RecommendResponse>();
  const [selectedMode, setSelectedMode] = useState<RecommendationMode>('STEP_BY_STEP');
  const [selectedSourceKeys, setSelectedSourceKeys] = useState<string[]>([]);
  const [savedRecommendations, setSavedRecommendations] = useState<SavedAiRecommendation[]>([]);
  const [aiLoading, setAiLoading] = useState<string>();
  const [aiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [closeOpen, setCloseOpen] = useState(false);
  const [escalateOpen, setEscalateOpen] = useState(false);
  const [aiForm] = Form.useForm();
  const [commentForm] = Form.useForm<CommentFormValues>();
  const [closeForm] = Form.useForm<{ resolutionComment: string }>();
  const [escalateForm] = Form.useForm<{ reason: string }>();

  const text = useMemo(() => `${ticket?.title ?? ''}\n${ticket?.description ?? ''}`.trim(), [ticket]);
  const lastComment = comments[0];
  const lastHistory = history[0];

  const sourceItems = useMemo<SourceItem[]>(() => {
    const articles = (similar?.articles ?? []).map((article) => ({
      key: `article-${article.articleId}`,
      type: 'article' as const,
      title: article.title,
      fit: article.fitPercent,
      description: article.content,
      meta: `Статья БЗ · ${article.category}`,
      color: 'blue'
    }));
    const cases = (similar?.resolvedCases ?? []).map((item) => ({
      key: `case-${item.ticketId}`,
      type: 'case' as const,
      title: `#${item.ticketId} ${item.title}`,
      fit: item.fitPercent,
      description: item.resolutionComment || 'Решение не заполнено',
      meta: 'Решённое обращение',
      color: 'green'
    }));
    const tickets = (similar?.tickets ?? []).map((item) => ({
      key: `ticket-${item.ticketId}`,
      type: 'ticket' as const,
      title: `#${item.ticketId} ${item.title}`,
      fit: Math.round(item.score * 100),
      description: 'Похожее обращение без готового решения. Используйте как сигнал для сравнения симптомов.',
      meta: 'Похожее обращение',
      color: 'purple'
    }));
    return [...articles, ...cases, ...tickets].sort((a, b) => b.fit - a.fit);
  }, [similar]);

  const selectedSourceHints = useMemo(() => sourceItems
    .filter((source) => selectedSourceKeys.includes(source.key))
    .map((source) => `${source.meta}: ${source.title} (${source.fit}%). ${source.description}`), [sourceItems, selectedSourceKeys]);

  const sourceQuality = useMemo(() => {
    const selected = sourceItems.filter((source) => selectedSourceKeys.includes(source.key));
    const best = selected.reduce((max, item) => Math.max(max, item.fit), 0);
    const score = Math.min(100, Math.round((selected.length ? 35 : 0) + Math.min(best, 100) * 0.45 + Math.min(selected.length, 4) * 5));
    const status = score >= 75 ? 'good' : score >= 45 ? 'normal' : 'weak';
    return { selected, best, score, status };
  }, [sourceItems, selectedSourceKeys]);

  const load = async () => {
    const [ticketData, commentsData, historyData, recommendationsData] = await Promise.all([
      ticketApi.getById(ticketId), ticketApi.comments(ticketId), ticketApi.statusHistory(ticketId),
      ticketApi.aiRecommendations(ticketId)
    ]);
    setTicket(ticketData); setComments(commentsData);
    setHistory(historyData); setSavedRecommendations(recommendationsData);
  };

  useEffect(() => { if (ticketId) load(); }, [ticketId]);

  useEffect(() => {
    if (sourceItems.length && !selectedSourceKeys.length) {
      setSelectedSourceKeys(sourceItems.filter((source) => source.fit >= 50).map((source) => source.key));
    }
  }, [sourceItems, selectedSourceKeys.length]);

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
      const result = await aiApi.similar(text);
      setSimilar(result);
      const nextItems = [
        ...(result.articles ?? []).map((article) => ({ key: `article-${article.articleId}`, fit: article.fitPercent })),
        ...(result.resolvedCases ?? []).map((item) => ({ key: `case-${item.ticketId}`, fit: item.fitPercent })),
        ...(result.tickets ?? []).map((item) => ({ key: `ticket-${item.ticketId}`, fit: Math.round(item.score * 100) }))
      ];
      setSelectedSourceKeys(nextItems.filter((source) => source.fit >= 50).map((source) => source.key));
      message.success('Источники подобраны. Проверьте их перед генерацией черновика.');
    } finally {
      setAiLoading(undefined);
    }
  };

  const generateDraft = async () => {
    setAiLoading('recommend');
    try {
      if (!similar) {
        await loadSources();
      }
      const result = await aiApi.recommend(text, selectedMode, selectedSourceHints);
      setDraft(result);
      message.success('Черновик готов. Он не сохранён — сохраните его вручную, если вариант подходит.');
    } finally {
      setAiLoading(undefined);
    }
  };

  const saveDraft = async () => {
    if (!draft) return;
    setAiLoading('saveDraft');
    try {
      const saved = await ticketApi.saveAiRecommendationDraft(ticketId, {
        recommendation: draft.recommendation,
        steps: draft.steps ?? [],
        mode: draft.explainability?.mode ?? selectedMode,
        sources: sourceQuality.selected.length ? sourceQuality.selected.map((source) => `${source.meta}: ${source.title}`) : (draft.explainability?.sources ?? []),
        llmStatus: draft.explainability?.llmStatus,
        rawModelOutput: draft.explainability?.rawModelOutput
      });
      setSavedRecommendations([saved, ...savedRecommendations]);
      message.success('Черновик сохранён в историю рекомендаций.');
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

  const setInProgress = async () => setTicket(await ticketApi.changeStatus(ticketId, 'IN_PROGRESS', 'Взято в работу'));

  const submitEscalation = async (values: { reason: string }) => {
    setTicket(await ticketApi.escalate(ticketId, values.reason));
    escalateForm.resetFields();
    setEscalateOpen(false);
  };

  const submitClose = async (values: { resolutionComment: string }) => {
    setTicket(await ticketApi.close(ticketId, values.resolutionComment));
    closeForm.resetFields();
    setCloseOpen(false);
  };

  const renderSourceCard = (source: SourceItem) => <Card key={source.key} size='small' className='ai-source-card'>
    <Space align='start' style={{ width: '100%', justifyContent: 'space-between' }}>
      <Checkbox checked={selectedSourceKeys.includes(source.key)} onChange={(event) => {
        setSelectedSourceKeys(event.target.checked ? [...selectedSourceKeys, source.key] : selectedSourceKeys.filter((key) => key !== source.key));
      }}>
        <Space direction='vertical' size={2}>
          <Typography.Text strong>{source.title}</Typography.Text>
          <Typography.Text type='secondary'>{source.meta}</Typography.Text>
        </Space>
      </Checkbox>
      <Tag color={source.color}>{source.fit}%</Tag>
    </Space>
    <Typography.Paragraph style={{ marginTop: 8, marginBottom: 0 }} ellipsis={{ rows: 3, expandable: true }}>{source.description}</Typography.Paragraph>
  </Card>;

  const sourceQualityType = sourceQuality.status === 'good' ? 'success' : sourceQuality.status === 'normal' ? 'normal' : 'exception';
  const sourceQualityMessage = sourceQuality.status === 'good'
    ? 'Источников достаточно: можно генерировать уверенный черновик.'
    : sourceQuality.status === 'normal'
      ? 'Источники есть, но проверьте релевантность перед сохранением.'
      : 'Источников мало или они не выбраны. Черновик может быть общим.';

  const classificationCard = <Card size='small' title='AI-классификация' extra={canOperate && <Button onClick={runClassification} loading={aiLoading === 'classify'}>Проверить классификацию AI</Button>}>
    {classify ? <Space direction='vertical' size={12} style={{ width: '100%' }}>
      <Alert showIcon type='info' message='AI предлагает классификацию, но изменения применяются только после подтверждения оператора.' />
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Tag>{categoryLabel[classify.category] ?? classify.category}</Tag></Col>
        <Col xs={24} md={8}><Tag color={priorityColor[classify.priority]}>{priorityLabel[classify.priority] ?? classify.priority}</Tag></Col>
        <Col xs={24} md={8}><Typography.Text>{classify.rationale}</Typography.Text></Col>
      </Row>
      <Form form={aiForm} onFinish={applyAi} layout='inline'>
        <Form.Item name='category' label='Категория'><Select style={{ width: 160 }} allowClear options={['GENERAL','INCIDENT','ACCESS','BILLING'].map((value) => ({ value, label: categoryLabel[value as keyof typeof categoryLabel] }))} /></Form.Item>
        <Form.Item name='priority' label='Приоритет'><Select style={{ width: 160 }} allowClear options={['LOW','MEDIUM','HIGH','URGENT'].map((value) => ({ value, label: priorityLabel[value as keyof typeof priorityLabel] }))} /></Form.Item>
        <Button htmlType='submit' type='primary'>Принять классификацию</Button>
      </Form>
      <ExplainabilityBlock explainability={classify.explainability} />
    </Space> : <Typography.Text type='secondary'>Классификация отделена от AI-помощника решения. Нажмите кнопку, чтобы получить предложение категории и приоритета.</Typography.Text>}
  </Card>;

  const overview = <Row gutter={[16, 16]} align='top'>
    <Col xs={24} xl={16}>
      <Space direction='vertical' size={16} style={{ width: '100%' }}>
        <Card title='Детали обращения'>
          <Descriptions column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label='Тема'>{ticket.title}</Descriptions.Item>
            <Descriptions.Item label='Статус'><Tag color={statusColor[ticket.status]}>{statusLabel[ticket.status] ?? ticket.status}</Tag></Descriptions.Item>
            <Descriptions.Item label='Категория'>{ticket.category ? (categoryLabel[ticket.category] ?? ticket.category) : '—'}</Descriptions.Item>
            <Descriptions.Item label='Приоритет'>{ticket.priority ? <Tag color={priorityColor[ticket.priority]}>{priorityLabel[ticket.priority] ?? ticket.priority}</Tag> : '—'}</Descriptions.Item>
            <Descriptions.Item label='Автор'>{ticket.requester?.name || ticket.requester?.username || '—'}</Descriptions.Item>
            <Descriptions.Item label='Исполнитель'>{ticket.assignedTo?.name || ticket.assignedTo?.username || 'Не назначен'}</Descriptions.Item>
            <Descriptions.Item label='Срок'>{ticket.resolutionDeadline ?? '—'}</Descriptions.Item>
            <Descriptions.Item label='Описание' span={2}><Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{ticket.description}</Typography.Paragraph></Descriptions.Item>
            <Descriptions.Item label='Решение' span={2}>{ticket.resolutionComment || '—'}</Descriptions.Item>
          </Descriptions>
        </Card>
        {classificationCard}
      </Space>
    </Col>
    <Col xs={24} xl={8}>
      <Space direction='vertical' size={16} style={{ width: '100%' }}>
        <Card title={`Комментарии: ${comments.length}`}>
          {lastComment ? <List.Item>
            <List.Item.Meta title={<Space>{lastComment.author.name || lastComment.author.username}{lastComment.internalComment && <Tag color='orange'>Внутренний</Tag>}</Space>} description={<Typography.Paragraph ellipsis={{ rows: 3, expandable: true }}>{lastComment.commentText}</Typography.Paragraph>} />
          </List.Item> : <Typography.Text type='secondary'>Комментариев пока нет</Typography.Text>}
        </Card>
        <Card title='Последнее изменение статуса'>
          {lastHistory ? <Timeline items={[{ children: <><Tag color={statusColor[lastHistory.toStatus]}>{statusLabel[lastHistory.toStatus] ?? lastHistory.toStatus}</Tag><div>{lastHistory.reason || 'Без комментария'}</div><Typography.Text type='secondary'>{lastHistory.createdAt}</Typography.Text></> }]} /> : <Typography.Text type='secondary'>История пока не заполнена</Typography.Text>}
        </Card>
      </Space>
    </Col>
  </Row>;

  const commentsTab = <Card title='Комментарии'>
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={14}><List dataSource={comments} locale={{ emptyText: 'Комментариев пока нет' }} renderItem={(item) => <List.Item>
        <List.Item.Meta title={<Space>{item.author.name || item.author.username}{item.internalComment && <Tag color='orange'>Внутренний</Tag>}</Space>} description={<><Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{item.commentText}</Typography.Paragraph><Typography.Text type='secondary'>{item.createdAt}</Typography.Text></>} />
      </List.Item>} /></Col>
      <Col xs={24} lg={10}>{canOperate || role === 'USER' ? <Form form={commentForm} layout='vertical' onFinish={addComment}>
        <Form.Item name='commentText' label='Новый комментарий' rules={[{ required: true, message: 'Введите комментарий' }]}><Input.TextArea rows={5} /></Form.Item>
        {canOperate && <Form.Item name='internalComment' valuePropName='checked'><Switch /> Внутренний комментарий</Form.Item>}
        <Button htmlType='submit' type='primary'>Добавить</Button>
      </Form> : <Alert type='info' showIcon message='Добавление комментариев недоступно.' />}</Col>
    </Row>
  </Card>;

  const historyTab = <Card title='История статусов'>
    <Timeline items={history.map((item) => ({ children: <><Tag color={statusColor[item.toStatus]}>{statusLabel[item.toStatus] ?? item.toStatus}</Tag><div>{item.reason || 'Без комментария'}</div><Typography.Text type='secondary'>{item.createdAt} · {item.changedBy?.name || item.changedBy?.username || 'Система'}</Typography.Text></> }))} />
  </Card>;

  const recommendationsTab = <Card title='История рекомендаций'>
    <List dataSource={savedRecommendations} locale={{ emptyText: 'Нет сохранённых рекомендаций' }} renderItem={
      (item) => <SavedRecommendationItem item={item} ticketId={ticketId} onUpdated={(updated) => setSavedRecommendations(savedRecommendations.map((r) => r.id === item.id ? updated : r))} />
    } />
  </Card>;

  const aiDrawer = <Drawer title={<Space><RobotOutlined />AI-помощник решения</Space>} width={760} open={aiDrawerOpen} onClose={() => setAiDrawerOpen(false)}>
    {!canOperate ? <Alert type='info' showIcon message='AI-помощник доступен оператору и администратору.' /> : <Space direction='vertical' size={16} style={{ width: '100%' }}>
      <Alert showIcon type='info' message='AI подберёт похожие решения и подготовит черновик. Черновик не сохраняется автоматически: сначала проверьте источники и формат.' />

      <Card title='1. Источники' extra={<Button onClick={loadSources} loading={aiLoading === 'sources'}>Найти похожие решения</Button>}>
        {similar ? <Space direction='vertical' size={12} style={{ width: '100%' }}>
          <Space style={{ width: '100%', justifyContent: 'space-between' }} align='center'>
            <div>
              <Typography.Text strong>{sourceQualityMessage}</Typography.Text>
              <div><Typography.Text type='secondary'>Выбрано источников: {sourceQuality.selected.length} из {sourceItems.length}. Лучшее совпадение: {sourceQuality.best || 0}%.</Typography.Text></div>
            </div>
            <Progress type='circle' size={72} percent={sourceQuality.score} status={sourceQualityType} />
          </Space>
          <Space wrap>
            <Button size='small' onClick={() => setSelectedSourceKeys(sourceItems.map((source) => source.key))}>Выбрать все</Button>
            <Button size='small' onClick={() => setSelectedSourceKeys(sourceItems.filter((source) => source.fit >= 70).map((source) => source.key))}>Только сильные</Button>
            <Button size='small' onClick={() => setSelectedSourceKeys([])}>Снять выбор</Button>
          </Space>
          {sourceItems.length ? <Space direction='vertical' size={8} style={{ width: '100%' }}>{sourceItems.map(renderSourceCard)}</Space> : <Empty description='Источники не найдены' />}
          <ExplainabilityBlock explainability={similar.explainability} />
        </Space> : <Empty description='Нажмите «Найти похожие решения», чтобы увидеть статьи и похожие обращения перед генерацией.' />}
      </Card>

      <Card title='2. Формат черновика'>
        <Radio.Group value={selectedMode} onChange={(event) => setSelectedMode(event.target.value)} className='ai-mode-grid'>
          {recommendationModes.map((mode) => <Radio.Button key={mode.value} value={mode.value} className='ai-mode-option'>
            <Typography.Text strong>{mode.label}</Typography.Text>
            <Typography.Text type='secondary'>{mode.description}</Typography.Text>
          </Radio.Button>)}
        </Radio.Group>
      </Card>

      <Card title='3. Черновик рекомендации' extra={<Space><Button onClick={generateDraft} loading={aiLoading === 'recommend'} type='primary'>Сгенерировать черновик</Button><Button onClick={saveDraft} loading={aiLoading === 'saveDraft'} disabled={!draft}>Сохранить</Button></Space>}>
        {draft ? <Space direction='vertical' size={12} style={{ width: '100%' }}>
          <Alert showIcon type='success' message='Черновик готов' description='Он может отличаться при следующей генерации и не попадёт в историю, пока вы не нажмёте «Сохранить».' />
          <div className='ai-output'>{renderFormattedAiText(draft.recommendation)}</div>
          {!!draft.steps?.length && <Card size='small' title='Первые действия'><ol>{draft.steps.map((step, index) => <li key={index}>{step}</li>)}</ol></Card>}
          <ExplainabilityBlock explainability={draft.explainability} />
        </Space> : <Empty description='Выберите формат и сгенерируйте черновик. Редактирования нет — если вариант не подходит, сгенерируйте новый.' />}
      </Card>
    </Space>}
  </Drawer>;

  return <Space direction='vertical' size={16} style={{ width: '100%' }}>
    <Card className='quick-actions-panel'>
      <Space direction='vertical' size={12} style={{ width: '100%' }}>
        <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>Обращение #{ticket.id}</Typography.Title>
            <Typography.Text type='secondary'>{ticket.title}</Typography.Text>
          </div>
          <Space wrap>
            <Tag color={statusColor[ticket.status]}>{statusLabel[ticket.status] ?? ticket.status}</Tag>
            {ticket.priority && <Tag color={priorityColor[ticket.priority]}>{priorityLabel[ticket.priority] ?? ticket.priority}</Tag>}
            {ticket.category && <Tag>{categoryLabel[ticket.category] ?? ticket.category}</Tag>}
          </Space>
        </Space>
        {canOperate && <Space wrap>
          <Button onClick={setInProgress}>В работу</Button>
          <Button onClick={() => setEscalateOpen(true)}>Эскалировать</Button>
          <Button type='primary' onClick={() => setCloseOpen(true)}>Закрыть</Button>
          <Button onClick={runClassification} loading={aiLoading === 'classify'}>Проверить классификацию AI</Button>
          <Badge count={draft ? 'черновик' : 0} offset={[-8, 0]}><Button type='primary' icon={<RobotOutlined />} onClick={() => setAiDrawerOpen(true)}>Открыть AI-помощник</Button></Badge>
        </Space>}
      </Space>
    </Card>

    <Tabs defaultActiveKey='overview' items={[
      { key: 'overview', label: 'Обзор', children: overview },
      { key: 'comments', label: `Комментарии (${comments.length})`, children: commentsTab },
      { key: 'history', label: `История (${history.length})`, children: historyTab },
      { key: 'recommendations', label: `Рекомендации (${savedRecommendations.length})`, children: recommendationsTab }
    ]} />

    {aiDrawer}

    <Modal title='Эскалация обращения' open={escalateOpen} onCancel={() => setEscalateOpen(false)} footer={null}>
      <Form form={escalateForm} layout='vertical' onFinish={submitEscalation}>
        <Form.Item name='reason' label='Комментарий для эскалации' rules={[{ required: true, message: 'Укажите комментарий эскалации' }]}><Input.TextArea rows={4} /></Form.Item>
        <Button htmlType='submit' type='primary'>Эскалировать</Button>
      </Form>
    </Modal>
    <Modal title='Закрытие обращения' open={closeOpen} onCancel={() => setCloseOpen(false)} footer={null}>
      <Form form={closeForm} layout='vertical' onFinish={submitClose}>
        <Form.Item name='resolutionComment' label='Комментарий решения' rules={[{ required: true, message: 'Укажите комментарий решения' }]}><Input.TextArea rows={4} /></Form.Item>
        <Button htmlType='submit' type='primary'>Закрыть обращение</Button>
      </Form>
    </Modal>
  </Space>;
}

function SavedRecommendationItem({ item, ticketId, onUpdated }: { item: SavedAiRecommendation; ticketId: number; onUpdated: (value: SavedAiRecommendation) => void }) {
  const [form] = Form.useForm<FeedbackFormValues>();
  const statusColorValue = item.accepted === true ? 'green' : item.accepted === false ? 'red' : 'blue';
  const statusText = item.accepted === true ? 'Принята' : item.accepted === false ? 'Отклонена' : 'Сохранена';
  return <List.Item>
    <Card size='small' style={{ width: '100%' }} title={<Space><HistoryOutlined />Рекомендация #{item.id}<Tag color={statusColorValue}>{statusText}</Tag>{item.usefulnessScore && <Tag color='gold'>{item.usefulnessScore}/5</Tag>}</Space>} extra={<Typography.Text type='secondary'>{item.createdAt}</Typography.Text>}>
      <Space direction='vertical' style={{ width: '100%' }} size={12}>
        <div className='ai-output'>{renderFormattedAiText(item.recommendation)}</div>
        <ExplainabilityBlock explainability={{ mode: item.mode, sources: item.sources, llmStatus: item.llmStatus, rawModelOutput: item.rawModelOutput, fallbackReason: item.fallbackReason }} />
        <Divider style={{ margin: '4px 0' }} />
        <Form form={form} layout='inline' onFinish={async (values) => {
          const updated = await ticketApi.evaluateAiRecommendation(ticketId, item.id, {
            accepted: Boolean(values.accepted), usefulnessScore: values.usefulnessScore,
            feedbackComment: values.feedbackComment });
          onUpdated(updated);
        }}>
          <Form.Item name='accepted' label='Принята' valuePropName='checked'><Switch /></Form.Item>
          <Form.Item name='usefulnessScore' label='Оценка'><Rate /></Form.Item>
          <Form.Item name='feedbackComment'><Input placeholder='Комментарий качества' /></Form.Item>
          <Button htmlType='submit'><CheckCircleOutlined />Оценить</Button>
        </Form>
      </Space>
    </Card>
  </List.Item>;
}
