-- 空对话的推荐问题。验收占位文案不再给消费者看。
-- 已有同文案或生效条数已满 12 时不再插入。

UPDATE chat_suggestion
SET status = 'retired',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'active'
  AND text LIKE '验收推荐%';

INSERT INTO chat_suggestion (id, text, sort, status, created_at, updated_at)
SELECT v.id, v.text, v.sort, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('01HCHATSUGGESTION00000001A', '我想买抽纸', 0),
    ('01HCHATSUGGESTION00000002A', '京东有货了吗', 1),
    ('01HCHATSUGGESTION00000003A', '我还有多少积分', 2),
    ('01HCHATSUGGESTION00000004A', '最近的订单发货了吗', 3),
    ('01HCHATSUGGESTION00000005A', '帮我找一副降噪耳机', 4)
) AS v(id, text, sort)
WHERE NOT EXISTS (
    SELECT 1 FROM chat_suggestion existing WHERE existing.text = v.text
)
  AND (SELECT COUNT(*) FROM chat_suggestion WHERE status = 'active') <= 7;
