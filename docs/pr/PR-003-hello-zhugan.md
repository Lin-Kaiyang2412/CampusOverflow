# PR #3 · 添加朱淦的自我介绍与成员模板

| 项目 | 内容 |
| --- | --- |
| PR 地址 | https://github.com/Lin-Kaiyang2412/CampusOverflow/pull/3 |
| 标题 | docs(team): 添加朱淦的自我介绍与成员模板 |
| 分支 | `feature/zhugan-hello` → `main` |
| 作者 | 朱淦（@zgabcd1） |
| 类型 | 文档（docs） |
| 变更规模 | 2 个文件，+91 行，0 删除 |
| 当前状态 | 待评审（等待 PO / 队友 LGTM） |

---

## 1. 变更内容

| 文件 | 说明 |
| --- | --- |
| `docs/team/hello-zhugan.md` | 朱淦的自我介绍：专业背景、技术栈、可贡献项、正在补的短板、协作偏好 |
| `docs/team/hello-TEMPLATE.md` | 其余成员的填写模板，内含分支命名与 PR 流程说明 |

## 2. 为什么这么做

1. **组队期最缺的是「谁知道什么」。** 把每个人的技能与短板写在仓库里，比在群里问一遍有效。
2. **首次 Git 协作需要一个低风险的载体。** 纯文档改动不碰基线框架的 107 个 TODO 方法，不会影响构建，适合作为全员的第一次 PR。
3. **模板降低后来者的门槛。** 后面 3-4 名成员照着 `hello-TEMPLATE.md` 就能独立走完「建分支 → 提交 → 发 PR → 等评审」，SM 不用每次现场讲一遍。

## 3. DoD 自检

| 条目 | 状态 |
| --- | --- |
| 从 `main` 之外的功能分支发起，命名符合 `feature/<拼音>-<简述>` | ✅ |
| 提交信息符合约定式提交（`docs(team): ...`），含 `Signed-off-by` | ✅ |
| 仅文档变更，不影响构建与测试 | ✅ |
| 无硬编码密钥 / 密码 / 内网地址 | ✅ |
| 无调试输出与临时文件 | ✅ |
| 获得 1 名队友的有效 LGTM（含理由） | ⏳ 待评审 |
| CI 状态检查通过 | ⏳ 待 CI 运行 |

## 4. 评审要点（给 reviewer）

1. `hello-zhugan.md` 中「正在补的短板」是否真实——我自评 DDD、React + TS、Docker 三项最弱，这直接影响任务分配，写得虚会误导组队。
2. `hello-TEMPLATE.md` 的流程说明是否够清楚，其他成员能否不靠口头讲解独立完成首次 PR。

## 5. 合并后验收

```bash
# main 分支应包含两份 hello 文档
git ls-tree -r --name-only main -- docs/team/ | grep hello
# 期望输出：
#   docs/team/hello-zhugan.md
#   docs/team/hello-TEMPLATE.md
```

## 6. 关联

- Lab 1 Step 3「Git 协作首次演练」
- 提交物 4：每位成员的首次 PR 合并记录（合并后在此补截图）
