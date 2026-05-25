import { Card, Col, Row, Statistic, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { slaApi } from '../api/slaApi';

export function AnalyticsPage(){ const [r,setR]=useState<any>(); useEffect(()=>{slaApi.report().then(setR).catch(()=>setR(null));},[]); return <><Typography.Title level={3}>Аналитика SLA</Typography.Title><Row gutter={16}><Col span={8}><Card><Statistic title='Всего' value={r?.total ?? 0}/></Card></Col><Col span={8}><Card><Statistic title='Нарушено SLA' value={r?.violated ?? 0}/></Card></Col><Col span={8}><Card><Statistic title='Средний FRT, мин' value={r?.avgFrtMinutes ?? 0}/></Card></Col></Row></>;}
