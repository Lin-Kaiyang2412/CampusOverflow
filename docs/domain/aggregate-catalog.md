# 聚合清单（Aggregate Catalog）· Lab 3 草案

> **状态：草案（DRAFT）** — 由组员D（朱淦，Developer + QA）整理，提交团队评审。
> 这是 Lab 3 四件套（事件风暴 / 上下文映射 / 聚合清单 / 术语表）中「聚合清单」一件。
> 其余三件（事件风暴、上下文映射、统一语言术语表）由组员A/组长分别负责。
>
> **本文档只描述「当前代码设计」**：依据 `origin/main` @ `bafad03`（107 个 `TODO(Sx)` 已实现后的版本）反向梳理，
> 每条结论都能指到对应的类 / 方法。**评审中提出、但代码尚未落地的建模选择，统一放在 §5「待讨论方案」**，
> 不与现状混写。若与事件风暴 / 上下文映射结论冲突，以团队最终设计为准。

## 0. 判定规则（本文档实际使用的口径）

| 概念 | 判定标准 | 本文档中的例子 |
| --- | --- | --- |
| **聚合根** | `domain` 包内有独立 `XxxRepository`，有独立身份，由仓储单独加载 / 保存 | `Question`、`Answer`、`Vote` |
| **实体** | 有身份，但只能经聚合根访问，`domain` 包内无独立仓储 | `CourseMember`（仅 `CourseMemberJpaRepository`） |
| **值对象** | 无身份，按属性判等 | `Title`、`MarkdownBody`、`Tags`、`TagName` |
| **领域服务** | 无状态，封装跨聚合规则或策略 | `ReputationRulebook`、`RelatedQuestionPolicy` |

> 判定以 `domain` 包内的 `XxxRepository` 接口为准；`infrastructure` 层的 `XxxJpaRepository` 是持久化实现细节，不作为聚合边界依据。

---

## 1. Q&A 上下文（co-qa）

| 聚合根 | 聚合内元素 | 与其他聚合的引用 | 不变式（均为当前代码行为） |
| --- | --- | --- | --- |
| **`Question`** | 值对象：`Title`、`MarkdownBody`、`Tags`、`TagName`；**无内部实体**（类中不持有 `Answer` / `Vote` / `Comment` 集合） | `acceptedAnswerId` 指向被采纳的回答；被 `Answer` / `Comment` 以 `questionId` 反向引用 | 仅作者 / 课程管理员可 `edit`；仅提问者或课程管理员可 `accept`；**已采纳后不能再次采纳**（`accept` 抛 `QA-2002` `ConflictException`）；`softDelete` 后 `ensureNotDeleted` 拦截后续操作；提问者只能删除尚无回答的问题（`answerCount > 0` 时拒绝） |
| **`Answer`** | 值对象：`MarkdownBody` | **独立聚合**，通过 `questionId` 引用 `Question`。类注释明确：「独立于 Question 聚合以降低并发冲突」。是否被采纳由 `Question.acceptedAnswerId` 单一事实来源决定，`Answer` 不重复存储 | `submit` 先调 `question.ensureOpenForAnswers()`：问题已删除或**已采纳**则拒绝新增回答；`edit` 仅作者 / 课管，且**不校验采纳状态——已采纳的回答仍可编辑**；`endorse` 仅课管且不可重复认证（`QA-2002`）；`softDelete` 时被采纳回答作者本人不能删、课管可删 |
| **`Vote`** | 值对象：`TargetType` | **独立聚合**，通过 `targetType` + `targetId` 引用 `Question` 或 `Answer`；**不属于 `Question` 聚合** | 同一用户对同一内容仅一票（数据库唯一约束兜底）；值只能 `+1` / `-1`；`cast` 拒绝自投（`voterId == targetAuthorId` 抛 `QA-1015`）；`change` 改投并返回原值；撤销即删除（`VoteRepository.delete`） |
| **`Comment`** | 值对象：`TargetType` | 有独立 `CommentRepository`；通过 `targetType` + `targetId` 引用问题或回答，另存 `questionId` 便于按问题查询 | 只提供 `post` / `reconstitute`，**无编辑、无删除方法**（`body`、`createdAt` 为 `final`）；支持一层二级回复（`parentId`） |

> **「采纳后锁定」的准确表述**：当前代码只锁定两件事——**不能再次采纳**、**不能继续新增回答**。
> `Answer.edit()` 只校验「未删除 + 作者/课管权限」，没有采纳状态校验，因此**已采纳的回答仍可由作者或课程管理员编辑**。
> 「采纳后冻结答案正文」是 §5 的待讨论方案，不是现状。

> **关于 `Answer` / `Comment` 的归属**：按 §0 的判定规则（`domain` 包内有独立仓储即聚合根），二者当前都是独立聚合，
> 与 `Question` 之间是「ID 引用 + 应用层编排」，不是对象图内的强一致组合。若团队希望收敛为 `Question` 内部实体，
> 属于建模调整，列在 §5，本文档不按那个口径写现状。

## 2. Identity 上下文（co-identity）

| 聚合根 | 聚合内元素 | 与其他聚合的引用 | 不变式 |
| --- | --- | --- | --- |
| **`User`** | 值对象 / 枚举：`UserStatus`、`Role`；领域服务：`PasswordHasher`、`PasswordPolicy`；**无内部实体** | 被 `CourseMember` 以 `userId` 引用 | 用户名 / 邮箱格式校验；`UserStatus` 控制账号启用 / 禁用；密码经 `PasswordHasher` 哈希、`PasswordPolicy` 校验强度；学生自助注册默认待认证，管理员创建教职工账号即视为已认证 |
| **`Course`** | 值对象：`CourseRole`（用于成员授权判定）；**无内部实体** | 被 `CourseMember` 以 `courseId` 引用 | 同一课程代码在同一学期唯一；`teacherId` 为课程负责人 |

**`CourseMember`（关联实体，未上提为聚合根）**：独立类，以 `(courseId, userId)` 标识，承载 `CourseRole` / `CourseMemberStatus`，
是「谁能在该课程中采纳、认证、悬赏」的授权依据。它**不在 `User` 或 `Course` 的对象图里**，`domain` 包内也没有 `CourseMemberRepository`，
目前由 `CourseMemberJpaRepository` 直接维护。其聚合归属见 §5。

## 3. Discovery 上下文（co-discovery）

| 聚合根 | 聚合内元素 | 说明 | 不变式 |
| --- | --- | --- | --- |
| **`Notification`** | 值对象：`NotificationType` | 独立聚合，`NotificationRepository` | `create` 生成通知；`markRead` 置已读；未读计数由 `NotificationService` 聚合查询 |

**非聚合部分**：

- **Search 查询模型**（读侧）：`SearchQuery`、`SearchDocument`、`SearchHit`、`SearchSort` 为查询 DTO / 值对象，端口为 `SearchPort`；
  由 `SearchIndexProjector` 监听领域事件维护索引，`RelatedQuestionPolicy`（领域服务）用 Jaccard 计算相关度。`SearchIndex` 是事件驱动的读模型，不计入写侧聚合。
- **`MentionParser`**：无状态领域服务，解析正文中的 `@提及`。

## 4. Reputation 上下文（co-reputation）

| 聚合根 | 聚合内元素 | 与其他聚合的引用 | 不变式 |
| --- | --- | --- | --- |
| **`ReputationAccount`** | 值对象：`ReputationReason`；**无内部实体**（类中不持有 `LedgerEntry` 集合） | 以 `userId` 标识账户；`LedgerEntry` 以 `userId` 引用账户 | `open` 开户；`applyReputation` 入账并校验范围（`REP-1008`）；`freezePoints` / `spendFrozen` / `refundFrozen` 处理冻结积分；`ensureFrozen` / `requirePositive` 守卫余额非负 |
| **`LedgerEntry`**（只追加账本） | 值对象：`ReputationReason` | 独立 `LedgerRepository`；通过 `userId` + `eventId` 关联账户与事件 | 只追加（ADR-004）；`(eventId, userId, reason)` 唯一，事件重复投递天然幂等 |
| **`Bounty`** | 值对象：`BountyStatus` | 独立 `BountyRepository` | `open` 开赏；`award` 颁奖；`close` 关闭；`isDue` 判断是否到期；`ensureOpen` 守卫状态机 |
| **`BadgeAward`**（徽章） | 值对象：`BadgeType`；领域服务：`BadgePolicy` | 独立 `BadgeRepository` | `BadgePolicy.forAcceptedCount` / `forReputation` 按规则授予徽章 |

**非聚合部分**：

- **`AcademicTerm`**（无状态工具类，非值对象）：私有构造器 + 静态方法，`termKey` / `weekKey` / `monthKey` 提供时间维度，供声誉周期结算。
- **`ReputationRulebook`**（领域服务）：`voteEffect` / `voteDelta` / `applyDailyCap` / `initialPoints` 集中封装计分规则，是声誉计算的单一事实来源。

---

## 5. 待讨论方案（均未在代码中落地）

> 以下条目是评审 / 建模讨论中提出、但**当前代码没有实现**的选择，单独列出以免与 §1–§4 的现状混淆。

| # | 议题 | 当前实现 | 可选方案 |
| --- | --- | --- | --- |
| 1 | `Answer` / `Comment` 是否收敛为 `Question` 内部实体 | 各自独立聚合 + 独立 `domain` 仓储 | 若团队更看重「一次加载整个问答对象图」的强一致，可把二者降为 `Question` 内部实体；代价是并发写冲突面变大（`Answer` 的类注释正是为此选择独立聚合） |
| 2 | `Vote` 是否随目标聚合 | 独立聚合，`targetType` + `targetId` 引用 | 可按目标拆成 `QuestionVote` / `AnswerVote` 并挂到各自聚合；当前实现选择统一 `Vote` 表 + 唯一约束 |
| 3 | 采纳后是否冻结答案正文 | **允许编辑**（`Answer.edit` 不校验采纳状态） | 若产品要求「采纳即定稿」，需在 `Answer.edit` 增加采纳状态校验；当前未实现 |
| 4 | `CourseMember` 的聚合归属 | 独立关联实体，无 `domain` 仓储，由 JPA 仓储维护 | 归 `Course`（成员属于课程）或归 `User`（选课属于用户）；需在上下文映射阶段定 |
| 5 | `Badge` 聚合根命名 | 聚合根为 `BadgeAward`，经 `BadgeRepository` 持久化 | 若后续引入「徽章定义」实体，需区分 `Badge`（定义）与 `BadgeAward`（授予记录） |

## 6. 跨聚合一致性

- **Q&A 内部**：`Question` / `Answer` / `Vote` / `Comment` 各自独立持久化，通过 ID 引用；`Question` 的 `answerCount` / `score`
  由 `QuestionRepository.adjustAnswerCount` / `adjustScore` 以原子 SQL 维护，不要求在应用层加载完整对象图。
- **Reputation 与 Q&A / Identity 解耦**：声誉经领域事件（事务性发件箱）异步更新，不要求跨聚合强一致。
- **Discovery 与 Q&A / Identity 解耦**：同样走事件投影，读模型最终一致。
- **强一致边界**集中在各聚合内部（如 `Question` 的采纳状态机、`ReputationAccount` 的余额守卫）。
