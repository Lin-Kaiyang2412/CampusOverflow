# Scrum Master 过程状态记录 · 2026-10-27

> 本文件是基于仓库公开提交、分支、Issue 和 Pull Request 的状态审计，不替代真实的站会、周会、Review 或 Retrospective 纪要。真实会议纪要应在取得参会信息后放入 `docs/meetings/`。

## 当前基线

| 项目 | 状态 |
| --- | --- |
| 默认分支 | `main` |
| 当前主分支提交 | `ca38d40` |
| 今日已合入的核心变更 | Q&A 应用流程、Reputation/Discovery 调用链和 CI 修复 |
| `TODO(Sx)` 静态检查 | 当前 `main` 未发现匹配项 |
| 本地测试复核 | `mvn -B test` 在当前环境未在检查窗口内完成，不能据此声明本地全绿 |

## 待跟踪 Pull Request

| PR | 主题 | 作者 | 过程关注点 |
| ---: | --- | --- | --- |
| [#21](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/21) | 前端 ESLint | Borair | 检查 lint、typecheck、build 和 Issue #8 关联 |
| [#22](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/22) | CI 接入 ESLint | 朱淦 | 当前目标分支基于前端功能分支，确认与 #21 的合并顺序 |
| [#24](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/24) | JaCoCo / Sonar 门禁 | 朱淦 | 检查覆盖率阈值与当前测试基线是否匹配 |
| [#25](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/25) | Lab 2 故事地图与验收标准 | 林恺洋 | 当前目标分支为 `develop`，由 PO 确认基线和合并方式 |
| [#26](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/26) | Prometheus 告警与 Alertmanager | 朱淦 | 检查规则、文档和运行环境证据 |
| [#27](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/27) | GHCR + SSH 部署流水线 | 朱淦 | 确认部署凭据、服务器和冒烟测试结果 |
| [#28](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/28) | PR base 分支触发 CI | 朱淦 | 检查堆叠 PR 是否真的获得 CI 验证 |
| [#29](https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/29) | S2 Postman Collection | 林恺洋 | 检查 Issue #7 验收场景和集合文件是否匹配 |

## 当前阻塞与下一步

1. PR #22 依赖前端分支基线，需要作者和 PO 明确先合并 #21，或将 #22 重新整理到 `main`。
2. PR #25 使用 `develop` 作为目标分支，需要 PO 确认这是有意的分支策略。
3. [Issue #23](https://github.com/Lin-Kaiyang2412/CampusOverflow/issues/23) 仍未分配负责人，内容包括声誉/发现错误码和回答编辑事件。
4. 需要收集团队真实会议内容，再提交 `docs/meetings/` 下的日期纪要。
5. 所有 PR 在合并前都需要保留 Issue、Review、CI 和验收证据。
