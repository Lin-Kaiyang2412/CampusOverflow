# 分支保护配置说明

> **状态**：⏳ 待执行 —— **必须由组长操作**。
> 实测（2026-10-22）：朱淦账号对该仓库 `permissions.admin = false`（仅 push），
> 调用分支保护 REST API 返回 **HTTP 404 Not Found**，无法代为配置。
> 课程常见陷阱第一条就是「成员各自在 main 上直接提交」，规避办法就是建仓后第一件事上保护。

## 1. 要达成的目标状态

| 保护项 | 目标值 | 为什么 |
| --- | --- | --- |
| `main` 禁止直接 push | ✅ 开启（含管理员） | 杜绝绕过评审 |
| 变更必须经 Pull Request | ✅ 开启 | 所有代码进 main 前可评审 |
| 合并前需审批数 | **1**（Sprint 0–1）→ **2**（Sprint 2 起） | 至少 1 名队友 LGTM |
| Dismiss stale approvals | ✅ 开启 | 改完代码旧 LGTM 作废 |
| Require review from Code Owners | ✅ 开启 | 配合 `.github/CODEOWNERS` |
| 合并前必须通过状态检查 | **Sprint 0–1 只勾 `前端构建`**；Sprint 2 起加 `后端构建与验证`；Lab 7 加覆盖率门禁 | 基线后端测试天然红，全勾会把 PR 全堵死，见 [ci-baseline-note.md](ci-baseline-note.md) |
| Require branches up to date | ✅ 开启 | 避免合并陈旧分支 |
| 禁止 force push | ✅ 开启 | 防止历史被改写 |
| 禁止删除分支 | ✅ 开启 | 防止误删 |

> ⚠️ **状态检查名字要对上实际工作流**。本仓库 `.github/workflows/ci.yml` 产出的检查项名称是
> **`前端构建`**、**`后端构建与验证`**、**`容器镜像构建`**，不是 `build` / `lint` / `test`。
> 在 GitHub 网页上勾选时，只有在**该检查项至少运行过一次之后**才会出现在候选列表里。

## 2. 方式 A：GitHub 网页（零工具依赖，推荐组长用）

`Settings → Branches → Add branch protection rule`

- Branch name pattern：`main`
- 勾选：
  - ✅ Require a pull request before merging → Required approvals: `1`
  - ✅ Dismiss stale pull request approvals when new commits are pushed
  - ✅ Require review from Code Owners
  - ✅ Require status checks to pass → 先只填 `前端构建`
  - ✅ Require branches to be up to date before merging
  - ✅ Require conversation resolution before merging
  - ✅ Do not allow bypassing the above settings（把管理员也一起管住）
  - ✅ Restrict who can push（仅保留管理员，或留空即禁止所有直推）
  - ✅ Do not allow force pushes / force deletions
- 保存。

## 3. 方式 B：REST API（本机无 gh CLI，用 curl + Token）

```bash
# 1) 准备 Fine-grained PAT 或 Classic PAT（repo 权限），存到环境变量，不要写进代码
export GH_TOKEN="<你的 token>"

# 2) 对 main 分支下发保护规则
curl -X PUT \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/<组织或用户名>/campus-overflow/branches/main/protection" \
  -d '{
    "required_status_checks": { "strict": true, "contexts": ["前端构建"] },
    "enforce_admins": true,
    "required_pull_request_reviews": {
      "dismiss_stale_reviews": true,
      "require_code_owner_reviews": true,
      "required_approving_review_count": 1
    },
    "restrictions": null,
    "allow_force_pushes": false,
    "allow_deletions": false,
    "required_linear_history": false,
    "required_conversation_resolution": true
  }'
```

返回 `200` 且 `"enabled": true` 即生效。

**校验**：

```bash
curl -H "Authorization: Bearer $GH_TOKEN" \
  "https://api.github.com/repos/<组织或用户名>/campus-overflow/branches/main/protection" | head -40
```

## 4. 方式 C：Gitee（备用）

`仓库 → 管理 → 分支管理 → 设置保护分支` → 选 `main` →
开启「禁止直接推送」「必须评审」「评审通过人数 ≥ 1」。

## 5. 配套的分支命名约定

| 类型 | 命名 | 示例 |
| --- | --- | --- |
| 功能 | `feature/<拼音>-<简述>` | `feature/zhugan-hello` |
| 修复 | `fix/<拼音>-<简述>` | `fix/zhugan-vote-count` |
| 文档 | `docs/<拼音>-<简述>` | `docs/zhugan-charter-v1` |
| 实验 | `lab/<编号>-<简述>` | `lab/1-team-setup` |

> ⚠️ 本机已知缺陷：本机 PortableGit 2.55 在**本地创建含斜杠的分支**会静默失败
> （`git switch -c feature/x` 返回成功，但 ref 实际未写入，甚至删除手工写入的同名 ref 文件）。
>
> 本仓库的规避方式：把分支写进 `.git/packed-refs`（该文件不受上述缺陷影响），
> 因此 `git branch` 里已能看到 `feature/zhugan-hello`。
> 推送时仍统一用显式 ref 映射，避免被本地 ref 状态影响：
> `git push origin feature/zhugan-hello:refs/heads/feature/zhugan-hello`
> 详见 [remote-setup.md](remote-setup.md)。

## 6. 验收标准

- [ ] `git push origin main` 返回 `remote: error: GH006: Protected branch update failed`
- [ ] 新建 PR 时页面显示 "1 approval required"
- [ ] 本文件第 1 节表格逐项核对通过，截图留存
