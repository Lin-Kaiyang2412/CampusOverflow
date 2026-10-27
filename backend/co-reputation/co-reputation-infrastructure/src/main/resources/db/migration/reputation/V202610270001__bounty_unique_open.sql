-- 一个问题最多允许一个 OPEN 悬赏（PR #33 评审意见 2）。
-- 生成列仅在 status='OPEN' 时取 1，其余状态为 NULL：MySQL 唯一索引不把 NULL 视为相等，
-- 因此历史已结束悬赏可共存，而同一问题只能有一行取 (question_id, 1)。
-- 并发发布时后写者会收到唯一键冲突，在 open() 事务内翻译为业务冲突码并整体回滚，
-- 失败请求冻结的积分随事务回滚，不会扣减。

-- 存量环境可能已有同题多条 OPEN（旧版本并发窗口真实存在），直接加唯一键会让 Flyway 卡死：
-- 先保留每题最早一条 OPEN，其余按 EXPIRED 关闭，消除存量冲突。
-- 被关闭的重复单不自动退款：它们本就不应存在，积分对账由人工核对（数量应为 0 或极少）。
UPDATE rep_bounty b
    JOIN (SELECT question_id, MIN(id) AS keep_id
          FROM rep_bounty
          WHERE status = 'OPEN'
          GROUP BY question_id
          HAVING COUNT(*) > 1) dup ON dup.question_id = b.question_id
    SET b.status = 'EXPIRED', b.closed_at = NOW(6)
WHERE b.status = 'OPEN' AND b.id <> dup.keep_id;

ALTER TABLE rep_bounty
    ADD COLUMN open_flag TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_bounty_question_open (question_id, open_flag);
