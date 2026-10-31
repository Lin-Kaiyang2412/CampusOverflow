# 聚合清单（Aggregate Catalog）· Lab 3 草案

> **状态：草案（DRAFT）** — 由组员D（朱淦，Developer + QA）整理，提交团队评审。
> 这是 Lab 3 四件套（事件风暴 / 上下文映射 / 聚合清单 / 术语表）中「聚合清单」一件。
> 其余三件（事件风暴、上下文映射、统一语言术语表）由组员A/组长分别负责，尚未产出。
>
> 本清单**依据已实现代码反向梳理**（`origin/main` @ bafad03，即 107 个 `TODO(Sx)` 已实现后的版本），
> 用于对齐团队的领域建模认知；若与事件风暴 / 上下文映射结论冲突，以团队最终设计为准。

## 识别规则

- **聚合根（Aggregate Root）**：拥有独立 `XxxRepository`、且对外暴露静态工厂（`post` / `open` / `reconstitute`）或强身份标识的领域类。
- **实体（Entity）**：有身份、依附于某个聚合根存在。
- **值对象（Value Object）**：无身份、靠属性判等（如 `Title`、`MarkdownBody`、`Tags`）。
- **领域服务（Domain Service）**：无状态、协调多个聚合（如 `ReputationRulebook`、`RelatedQuestionPolicy`、`MentionParser`）。

---

## 1. Q&A 上下文（co-qa）

| 聚合 | 聚合根 | 内部实体 / 值对象 | 不变式（invariant） |
| --- | --- | --- | --- |
| **Question 聚合** | `Question` | 实体：`Answer`、`Vote`、`Comment`；值对象：`Title`、`MarkdownBody`、`Tags`、`TagName`、`TargetType` | 仅作者 / 课程管理员可 `edit`；仅提问者或课程管理员可 `accept`；`accept` 后答案锁定；`softDelete` 后 `ensureOpenForAnswers` / `ensureNotDeleted` 拦截后续操作 |
| **Vote 聚合**（投票作为独立聚合，拥有 `VoteRepository`） | `Vote` | 值对象：`TargetType` | `cast` 记录投票方向；`change` 改方向；`checkValue` 校验合法性 |

> 注：`Answer`、`Comment` 虽各有 Repository，但从业务边界看它们生命周期依附于 `Question`，
> 这里按「强一致性边界」归入 Question 聚合；若团队按「每个 Repository = 一个聚合」建模，`Answer` / `Comment` 可上提为独立聚合。此项需团队在上下文映射阶段拍板。

## 2. Identity 上下文（co-identity）

| 聚合 | 聚合根 | 内部实体 / 值对象 | 不变式 |
| --- | --- | --- | --- |
| **User 聚合** | `User` | 实体：`CourseMember`（含 `CourseMemberStatus`、`CourseRole`）；领域服务：`PasswordHasher`、`PasswordPolicy` | `UserStatus` 控制账号启用/禁用；密码经 `PasswordHasher` 哈希、`PasswordPolicy` 校验强度 |
| **Course 聚合** | `Course` | 实体：`CourseMember` | 课程与成员关系由 `CourseMember` 表达，双向归属 User 与 Course |

> `CourseMember` 同时出现在 User 与 Course 两侧，是跨聚合的关联实体；建议约定以其中一侧为聚合根、另一侧仅持引用，避免双向强一致。

## 3. Discovery 上下文（co-discovery）

| 聚合 | 聚合根 | 内部实体 / 值对象 | 不变式 |
| --- | --- | --- | --- |
| **Notification 聚合** | `Notification` | 值对象：`NotificationType` | `create` 生成通知；`markRead` 置已读；未读计数由 `NotificationService` 聚合查询 |
| **Search 查询模型**（非聚合，查询侧） | — | 值对象：`SearchQuery`、`SearchDocument`、`SearchHit`、`SearchSort`；端口：`SearchPort` | 由 `SearchIndexProjector` 监听领域事件维护索引；`RelatedQuestionPolicy`（领域服务）用 Jaccard 做相关度排序 |

> Discovery 偏「投影 / 查询」职责：`MentionParser`、`RelatedQuestionPolicy` 为无状态领域服务；
> `SearchIndex` 是事件驱动的读模型，不计入写侧聚合。

## 4. Reputation 上下文（co-reputation）

| 聚合 | 聚合根 | 内部实体 / 值对象 | 不变式 |
| --- | --- | --- | --- |
| **ReputationAccount 聚合** | `ReputationAccount` | 实体：`LedgerEntry`（账本明细，`LedgerRepository`）；值对象：`ReputationReason` | `open` 开户；`applyReputation` 入账；`freezePoints` / `spendFrozen` / `refundFrozen` 处理冻结资金；`ensureFrozen` / `requirePositive` 守卫余额非负 |
| **Bounty 聚合** | `Bounty` | 值对象：`BountyStatus` | `open` 开赏；`award` 颁奖；`close` 关闭；`isDue` 判断是否到期；`ensureOpen` 守卫状态机 |
| **Badge（徽章）聚合** | `BadgeAward`（经 `BadgeRepository`） | 值对象：`BadgeType`；领域服务：`BadgePolicy` | `BadgePolicy.forAcceptedCount` / `forReputation` 按规则授予徽章 |
| **AcademicTerm（值对象）** | — | 值对象 | `termKey` / `weekKey` / `monthKey` 提供时间维度，供声誉周期结算 |
| **ReputationRulebook（领域服务）** | — | — | `voteEffect` / `voteDelta` / `applyDailyCap` / `initialPoints` 集中封装计分规则，是声誉计算的单一事实来源 |

---

## 跨聚合一致性

- **Reputation 与 Q&A / Identity 解耦**：声誉经领域事件（事务性发件箱）异步更新，不要求跨聚合强一致。
- **Discovery 与 Q&A / Identity 解耦**：同样走事件投影，读模型最终一致。
- **强一致边界**集中在各上下文内部（如 Question 聚合内的 accept 锁定、ReputationAccount 的余额守卫）。

## 待团队确认（开放问题）

1. `Answer` / `Comment` 是否提升为独立聚合（依 Repository 独立性），还是归属 Question 聚合？
2. `CourseMember` 的聚合归属（User 侧 / Course 侧）？
3. `Badge` 的聚合根应取 `BadgeAward` 还是另有 `Badge` 实体？
4. 以上若与事件风暴 / 上下文映射产出冲突，以团队设计为准，本清单同步修订。
