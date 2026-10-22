# 个人技能矩阵自评 · 朱淦

> 评分标准：1 = 未接触 / 2 = 能照着做 / 3 = 能独立完成常规任务 / 4 = 能带别人做 / 5 = 能定方案
> 用途：Lab 1 组队时按「互补原则」找搭档，并作为 Sprint 内结对与知识传递的依据。

| 能力域 | 具体技能 | 自评 | 依据 | 本 Sprint 目标 |
| --- | --- | ---: | --- | ---: |
| 语言 | Java（OOP / 集合 / 异常 / IO / JDBC） | 4 | 实训期独立完成学生信息管理系统四层架构，迭代 3 个版本 | 4（保持，转向并发与 JUC） |
| 语言 | SQL / MySQL（建表约束、多条件查询、分组聚合、分页） | 4 | 熟练，Navicat 日常使用 | 4 |
| 语言 | TypeScript | 1 | 未系统使用 | 2（Lab 5 前补基础类型与泛型） |
| 后端 | Spring Boot 3 | 3 | CRM 项目完成依赖配置、分层包结构、登录全链路 | 4（Lab 4 独立搭多模块骨架） |
| 后端 | MyBatis / FreeMarker | 3 | CRM 项目实战，会写映射与驼峰自动映射 | 3 |
| 后端 | Servlet / Tomcat | 2 | 了解，未独立部署 | 3 |
| 后端 | DDD / 限界上下文 / 聚合 | 1 | 仅理论学习，未落地 | 3（Lab 3 事件风暴后能写聚合代码） |
| 工程化 | Maven（多模块 / 生命周期 / 依赖管理） | 3 | CRM 与 campus-todo 项目实战 | 4（Lab 4 搭六模块父 POM） |
| 工程化 | Git（功能分支 / 小步提交 / 冲突处理 / squash merge） | 4 | campus-todo 承接 Issue #3，3 次小步提交、PR #4 经评审 squash merge | 5（团队内主导分支策略） |
| 工程化 | GitHub Flow / PR 评审 | 4 | 完成 2 次非本人 PR 评审（PR #5 Approve；PR #6 提 3 条阻塞意见并复审） | 5（带新成员走完首次 PR） |
| 工程化 | GitHub Actions | 3 | campus-todo 配置 Maven CI（Temurin JDK 17 + 缓存 + surefire 报告上传），build 23s 通过 | 4（Lab 8 扩展为完整流水线） |
| 工程化 | Docker / docker-compose | 1 | 本机尚未安装，未用过 | 2（Lab 4 前装好并跑通 MySQL 容器） |
| 测试 | JUnit 5 / Mockito | 2 | JUnit 了解，未写过复杂断言与 ArgumentCaptor | 3（Lab 7 覆盖率达标） |
| 测试 | Gherkin / 验收标准 | 3 | 能写出可测试的 Given-When-Then | 4 |
| 前端 | HTML / CSS / JS / AJAX | 2 | 了解，能对接后端接口 | 3（Lab 5 能改页面） |
| 前端 | React 18 + Hooks / TanStack Query | 1 | 未接触 | 2（Lab 5 跟做） |
| 质量 | SonarQube / JaCoCo | 2 | 看过 JaCoCo 报告，未配过质量门禁 | 3（Lab 7 配置门禁） |
| 协作 | Scrum 流程 / 站会主持 | 3 | 理解流程，主持经验少 | 4 |
| 协作 | 技术文档写作 | 4 | 实验报告、PR 模板、README 规范化有积累 | 4（承担团队文档主笔） |
| 领域 | 需求访谈 / 用户故事 | 3 | 能提炼，但访谈经验少 | 4（Lab 2 主导故事地图） |

## 自评结论

**优势**：Java 后端工程实践（Spring Boot + MyBatis + MySQL）、GitHub Flow 协作（分支 / PR / 评审 / squash merge 全流程实操过）、CI 配置、文档写作。

**短板**：DDD 从建模到代码落地、React + TypeScript、Docker 与容器编排、单元测试深度（JUnit 只到「了解」）。

## 组队互补需求（按优先级）

1. **1 名前端（React + TypeScript）较强的 Developer** —— 补足 Lab 5；我负责后端与 API 契约，他负责前端。
2. **1 名有 DDD / 架构经验或学习意愿强的 Developer** —— Lab 3 事件风暴与 Lab 4 聚合设计，与我结对。
3. **1 名熟悉 Docker / 监控的 Developer** —— 补足 Lab 8 部署与 Prometheus / Grafana。
4. **PO 由组长担任** —— 我不代行，专注 SM + 后端。

## 技能传递计划

- 我给团队做一次 30 分钟分享：**campus-todo 的 GitHub Flow 实操复盘**（Issue 认领 → 功能分支 → 小步提交 → PR 自检清单 → 评审意见分级 → squash merge → CI 门禁），直接服务于 Lab 1 的 Git 演练。
- 我向强前端成员学 React + TanStack Query，向强架构成员学聚合划分；作为交换，我讲后端分层与 MyBatis 映射。
- 每周站会末尾 5 分钟「短板播报」，各自说明本周补了哪一项。
