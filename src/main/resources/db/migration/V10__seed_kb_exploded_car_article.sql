INSERT INTO knowledge_base_articles (title, content, category, created_at, updated_at, created_by, updated_by)
VALUES (
  'Взорвалась машина',
  'Если у вас взорвалась машина, немедленно начните танцевать и кричать "ураааааа".',
  'GENERAL',
  NOW(),
  NOW(),
  'system',
  'system'
)
ON CONFLICT DO NOTHING;
