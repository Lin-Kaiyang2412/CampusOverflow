-- 一个问题最多允许一个 OPEN 悬赏（PR #33 评审意见 2）。
-- 生成列仅在 status='OPEN' 时取 1，其余状态为 NULL：MySQL 唯一索引不把 NULL 视为相等，
-- 因此历史已结束悬赏可共存，而同一问题只能有一行取 (question_id, 1)。
-- 并发发布时后写者会收到唯一键冲突，在 open() 事务内翻译为业务冲突码并整体回滚，
-- 失败请求冻结的积分随事务回滚，不会扣减。
ALTER TABLE rep_bounty
    ADD COLUMN open_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_bounty_question_open (question_id, open_flag);
