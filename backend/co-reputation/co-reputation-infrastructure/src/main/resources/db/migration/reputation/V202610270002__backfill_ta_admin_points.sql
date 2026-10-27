-- 存量 TA / ADMIN 账户补发初始悬赏积分（PR #37 评审意见：initialPoints 只覆盖新注册账号）。
-- 这些角色的账户在旧版本注册时以 0 分创建，导致有悬赏权限却永远积分不足。
-- 只补从未获得过积分流水、可用/冻结/声誉均为 0 的存量账户，避免已花光积分者被重复补发。
UPDATE rep_account a
    JOIN id_user u ON u.id = a.user_id
    SET a.available_points = 1000
WHERE u.role IN ('TA', 'ADMIN')
  AND a.available_points = 0
  AND a.frozen_points = 0
  AND a.reputation = 0
  AND NOT EXISTS (SELECT 1 FROM rep_ledger l WHERE l.user_id = a.user_id);
