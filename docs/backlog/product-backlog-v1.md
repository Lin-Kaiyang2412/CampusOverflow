# CampusOverflow Product Backlog v1

> 版本：v1（Lab 2 需求建模初版，Sprint 1 使用）
> 依据：《CampusOverflow 实验指导书 V2》附录 A（107 个 TODO 与 PBI/Sprint 映射）与仓库 Issues #1–#15
> 估算：Story Point ∈ {1, 2, 3, 5}（Fibonacci 子集）；优先级 P0 > P1 > P2

## 1. Backlog 列表

| 故事 ID | 用户故事（作为…我想…以便…） | 功能域 | 优先级 | Story Point | 对应 Issue | 计划 Sprint |
|---|---|---|---|---|---|---|
| US-001 | 作为访客，我想注册并登录 CampusOverflow，以便使用问答功能 | Identity | P0 | 3 | #1 | Sprint 0 |
| US-002 | 作为学生，我想加入课程，以便在我的课程上下文中提问与浏览 | Identity | P0 | 2 | #1 | Sprint 0 |
| US-003 | 作为学生，我想提交选课申请并完善个人资料，以便管理课程身份 | Identity（扩展） | P2 | 3 | #2 | Sprint 4（可选） |
| US-004 | 作为学生，我想发布问题（标题 10–150 字符、Markdown 正文、1–5 个标签），以便获得同学解答 | Q&A | P0 | 5 | #4 / #6 | Sprint 1 |
| US-005 | 作为学生，我想浏览问题列表并按标签/关键词筛选，以便找到感兴趣的问题 | Q&A | P0 | 3 | #6 | Sprint 1 |
| US-006 | 作为学生，我想查看问题详情与全部回答，以便了解最佳解答 | Q&A | P0 | 2 | #6 | Sprint 1 |
| US-007 | 作为学生，我想回答问题，以便帮助提问者 | Q&A | P0 | 3 | #4 / #6 | Sprint 1 |
| US-008 | 作为提问者，我想采纳一个回答为最佳答案（每题一次且不可更改），以便标记问题已解决 | Q&A | P0 | 2 | #4 / #6 | Sprint 1 |
| US-009 | 作为用户，我想对问题/回答投票（+1/−1，每人每内容一票，可改投），以便体现内容质量 | Q&A | P0 | 2 | #4 / #6 | Sprint 1 |
| US-010 | 作为用户，我想发表评论（最多一层回复），以便补充说明 | Q&A | P0 | 1 | #4 / #6 | Sprint 1 |
| US-011 | 作为用户，我想查看热门标签，以便发现热点话题 | Q&A | P0 | 2 | #6 | Sprint 1 |
| US-012 | 作为其他上下文服务，QaQueryApi 提供问题/答案只读查询，以便跨上下文协作（OHS/PL） | Q&A | P0 | 3 | #6 | Sprint 1 |
| US-013 | 作为用户，我的问答行为会自动结算声誉（采纳 +15、被赞 +10、被踩 −2，每日上限 200），以便激励高质量贡献 | Reputation | P1 | 5 | #10 | Sprint 2 |
| US-014 | 作为用户，我会按采纳数与声誉获得徽章，以便获得社区认可 | Reputation | P1 | 3 | #10 | Sprint 2 |
| US-015 | 作为提问者，我可以为问题发起悬赏（OPEN→AWARDED/EXPIRED），以便吸引高质量回答 | Reputation | P1 | 3 | #10 | Sprint 2 |
| US-016 | 作为用户，我可以查看周/月/学期排行榜，以便了解社区活跃度 | Reputation | P1 | 3 | #10 | Sprint 2 |
| US-017 | 作为用户，我可以查看声誉档案与流水明细，以便核对积分来源 | Reputation | P1 | 3 | #10 | Sprint 2 |
| US-018 | 作为管理员，我可以执行声誉对账，以便保证账户余额=流水之和（FF-8） | Reputation | P1 | 3 | #10 | Sprint 2 |
| US-019 | 作为用户，我可以按关键词（含中文）搜索问题，以便快速找到相关内容 | Discovery | P1 | 5 | #9 | Sprint 3 |
| US-020 | 作为用户，我可以在问题详情页看到相关问题推荐，以便扩展阅读 | Discovery | P1 | 3 | #9 | Sprint 3 |
| US-021 | 作为用户，我会收到站内通知（回答被采纳、@提及等），以便及时跟进 | Discovery | P1 | 3 | #9 | Sprint 3 |
| US-022 | 作为用户，我可以查看通知中心并标记已读，以便管理消息 | Discovery | P1 | 2 | #9 | Sprint 3 |
| US-023 | 作为团队，CI 按 Sprint 渐进开启单元测试/覆盖率/集成测试门禁，以便保持主分支绿色 | 质量门禁 | P1 | 3 | #12 / #13 | Sprint 4 |
| US-024 | 作为运维者，我可以查看 Grafana 监控指标并处理告警，以便系统可观测 | 可观测性 | P1 | 3 | #14 | Sprint 4 |
| US-025 | 作为管理员，我可以一键部署线上系统并依据运维手册运维，以便完成 v1.0 发布 | 部署 | P1 | 5 | #15 | Sprint 4 |

## 2. 统计

- 故事总数：25 条（P0=11，P1=13，P2=1）
- P0 对应 Sprint 1 核心链路（US-001/002 + US-004~012 = 注册登录 → 提问 → 回答 → 投票 → 评论 → 采纳 → 热门标签 → QaQueryApi），全部落在 Sprint 1 的 36 个 TODO 方法（Q&A 上下文）与前端骨架联调范围
- P1 对应 Sprint 2（Reputation，39 个 TODO）与 Sprint 3（Discovery，32 个 TODO）及 Sprint 4 质量/部署
- P2 为可选扩展（原 README S1 扩展 Identity，按 V2 指导书降为低优先级 Backlog 项）

## 3. 说明

- 故事 ID 与仓库 Issue 的对应关系：多个 US 可映射同一 Issue（Issue 为里程碑级 PBI，故事为可拆任务）；Sprint 1 计划会拆分为 Task List 子任务
- 验收标准（Gherkin）见 `docs/requirements/gherkin/`，每条标注对应 US-ID
