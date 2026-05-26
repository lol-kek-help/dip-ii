import { Card, Input, List, Modal, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { knowledgeApi, KnowledgeArticle } from '../api/knowledgeApi';

export function KnowledgePage(){
  const [q,setQ]=useState(''); const [items,setItems]=useState<KnowledgeArticle[]>([]); const [selected,setSelected]=useState<KnowledgeArticle|null>(null);
  const load = async (text?: string) => setItems(await knowledgeApi.list(text));
  useEffect(()=>{ load(); },[]);
  return <Card title='База знаний'><Space direction='vertical' style={{width:'100%'}}><Input.Search placeholder='Поиск по базе знаний' enterButton onSearch={load} value={q} onChange={(e)=>setQ(e.target.value)} />
    <List dataSource={items} renderItem={(a)=><List.Item actions={[<a key='open' onClick={async()=>setSelected(await knowledgeApi.getById(a.id))}>Открыть</a>]}><List.Item.Meta title={a.title} description={<><Tag>{a.category}</Tag></>} /></List.Item>} />
  </Space>
  <Modal open={!!selected} onCancel={()=>setSelected(null)} footer={null} title={selected?.title}><Typography.Paragraph>{selected?.content}</Typography.Paragraph></Modal>
  </Card>;
}
