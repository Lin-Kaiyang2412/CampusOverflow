# 分支保护配置说明

> **状态**：✅ 已生效（2026-10-27 复核）
> **复核人**：朱淦（组员D · QA）
> 本文档最初写于 2026-10-22 建仓阶段（当时为「待执行」），2026-10-27 按仓库实际状态更新。

## 1. 现状（可验证的部分）

| 观察 | 结论 | 证据 |
| --- | --- | --- |
| `main` 不接受未经评审的合并 | 保护已开启 | 全部开放 PR 的 `mergeable_state` 均为 `blocked`，即使已有 1 个 approve（#33 / #34 / #36 均如此） |
| 合并前必须有人评审 | 保护已开启 | 已合并的 11 个 PR（#16 #18 #19 #22 #24 #26 #27 #28 #30 #31 #32）**每一个都含组长的 APPROVED** |
| 直接推送到 `main` | 未见发生 | `main` 上每次 push 事件均可对应一次 PR 合并，无来源不明的直推 |
| 成员权限 | 只有组长是 admin | 5 名协作者中 `Lin-Kaiyang2412` 为 `admin`，其余 4 人为 `write` |

> ⚠️ 具体的规则项（审批人数、是否强制 Code Owners、必需检查清单）**无法由成员账号读取**：
> `GET /repos/{owner}/{repo}/branches/main/protection` 对非 admin 返回 **HTTP 404**。
> 上表结论由「合并行为反推」得出，不是直接读取配置。**如需逐项确认，请组长登录后在
> `Settings → Branches` 截图留存**，作为 Lab 1 提交物 1 的正式证据。

## 2. 目标状态

| 保护项 | 目标值 | 为什么 |
| --- | --- | --- |
| `main` 禁止直接 push | ✅ 开启（含管理员） | 杜绝绕过评审 |
| 变更必须经 Pull Request | ✅ 开启 | 所有代码进 main 前可评审 |
| 合并前需审批数 | **1** | 至少 1 名队友带理由的 LGTM |
| Dismiss stale approvals | ✅ 开启 | 改完代码旧 LGTM 自动作废 |
| Require review from Code Owners | ⚠️ **待定** | 仓库尚未配置 `.github/CODEOWNERS`，勾了也是空转，见第 5 节 |
| 合并前必须通过状态检查 | ✅ `Backend build and verification` + `Frontend build` | 见第 3 节的名称对照 |
| Require branches up to date | ✅ 开启 | 避免合并陈旧分支 |
| 禁止 force push / 删除分支 | ✅ 开启 | 防止历史被改写或误删 |
| 对话未解决不可合并 | ✅ 开启 | 评审意见必须闭环 |

## 3. 状态检查名称对照（配置时最容易踩的坑）

GitHub 上勾选必需检查时，填的必须是 **workflow 里 job 的 `name`**，不是 job id、也不是
目录名。本仓库 `.github/workflows/ci.yml` 当前产出：

| job id | 页面上显示的名字 | 建议设为必需 |
| --- | --- | --- |
| `backend` | `Backend build and verification` | ✅ 是 |
| `frontend` | `Frontend build` | ✅ 是 |
| `images` | `容器镜像构建与推送` | ❌ 否（PR 事件下会 skip） |
| `deploy` | `部署到测试服务器` | ❌ 否（仅 main push 且需 `vars.DEPLOY_ENABLED=true`） |

> ⚠️ **不要把 `images` / `deploy` 设为必需检查**。这两个 job 在 `pull_request` 事件下
> 恒为 `skipped`，一旦设为必需，所有 PR 会永久卡在「等待检查」而无法合并。
>
> ⚠️ 检查项只有在**至少运行过一次之后**才会出现在候选列表里，所以顺序是：先推一个 PR
> 让 CI 跑一遍，再去 `Settings → Branches` 勾选。

## 4. 配置方式

### 方式 A：GitHub 网页（推荐）

`Settings → Branches → Add branch protection rule`，Branch name pattern 填 `main`，勾选：

- ✅ Require a pull request before merging → Required approvals: `1`
- ✅ Dismiss stale pull request approvals when new commits are pushed
- ✅ Require status checks to pass → 选 `Backend build and verification`、`Frontend build`
- ✅ Require branches to be up to date before merging
- ✅ Require conversation resolution before merging
- ✅ Do not allow bypassing the above settings
- ✅ Do not allow force pushes / Do not allow deletions

### 方式 B：REST API

```bash
export GH_TOKEN="<管理员 token，不要写进代码或提交>"

curl -X PUT \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Lin-Kaiyang2412/CampusOverflow/branches/main/protection" \
  -d '{
    "required_status_checks": {
      "strict": true,
      "contexts": ["Backend build and verification", "Frontend build"]
    },
    "enforce_admins": true,
    "required_pull_request_reviews": {
      "dismiss_stale_reviews": true,
      "require_code_owner_reviews": false,
      "required_approving_review_count": 1
    },
    "restrictions": null,
    "allow_force_pushes": false,
    "allow_deletions": false,
    "required_conversation_resolution": true
  }'
```

返回 `200` 且 `"enabled": true` 即生效。

## 5. 待办：CODEOWNERS 尚未配置

仓库当前**没有** `.github/CODEOWNERS`。如果分支保护勾选了「Require review from Code Owners」，
在没有 CODEOWNERS 文件的情况下该项不产生任何约束（等于没勾）。

如需启用，添加 `.github/CODEOWNERS`：

```
# 默认由组长把关全部路径
*                       @Lin-Kaiyang2412

# 质量门禁与 CI 配置由 QA 共同把关
/.github/workflows/     @Lin-Kaiyang2412 @zgabcd1
/backend/pom.xml        @Lin-Kaiyang2412 @zgabcd1
```

加完之后再回 `Settings → Branches` 把该项打开。**未加文件前请保持关闭**，否则会让人误以为
有强制评审、实际没有。

## 6. 分支命名约定

本仓库实际使用的命名（按 2026-10-27 的远程分支统计）：

| 类型 | 前缀 | 示例 |
| --- | --- | --- |
| 功能 | `feat/` | `feat/identity-enrollment-profile` |
| 修复 | `fix/` | `fix/issue-23-tech-debt` |
| 文档 | `docs/` | `docs/lab2-deliverables` |
| CI / 流水线 | `ci/` | `ci/lint-gate` |
| 测试 / 门禁 | `test/` | `test/coverage-gate` |
| 运维 / 监控 | `ops/` | `ops/monitoring-alerts` |

约定：`<类型>/<kebab-case-英文简述>`，小写、连字符分隔，不复用拼音。
长期分支只有 `main` 与 `develop`。

## 7. 验收标准

- [x] 未经评审的 PR 无法合并（`mergeable_state = blocked`）
- [x] 已合并 PR 全部经过至少 1 次评审
- [ ] `git push origin main` 被拒绝（`remote: error: GH006: Protected branch update failed`）—— 需组长实测并截图
- [ ] `Settings → Branches` 规则页截图留存 —— 需组长提供，作为 Lab 1 提交物 1
