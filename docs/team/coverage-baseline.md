# 覆盖率基线与质量门禁（Lab 7）

> 维护人：组员D（Developer + QA）
> 数据来源：`mvn -B -f backend/pom.xml verify -DskipITs` 生成的 JaCoCo 报告
> 快照时间：**2026-10-30**（main `a2636be`，即 PR #34 / #35 合并之后）
> 上一版快照为 2026-10-27（PR #20 状态），本次为全量重测，非估算。

## 1. 门禁规则

| 范围 | 指标 | 阈值 | 配置位置 |
|---|---|---|---|
| 领域层（`com/campusoverflow/*/domain/**`） | 行覆盖率 | **0.70** | `backend/pom.xml` → `domain.coverage.minimum` |
| 核心聚合 `co-qa-domain` | 行覆盖率 | **0.80** | `backend/co-qa/co-qa-domain/pom.xml` 覆盖属性 |
| 全部模块（SonarQube 服务端） | Bugs / Vulnerabilities | **0** | SonarQube Quality Gate |
| 全部模块（SonarQube 服务端） | 覆盖率 | **70%** | SonarQube Quality Gate |

门禁在 `verify` 阶段执行，未达标直接中断构建（`jacoco:check`），因此 CI 的「覆盖率门禁」步骤会自动生效。

> 只对 4 个领域模块启用：`co-shared-kernel`、`co-bootstrap`、各 `application` / `infrastructure` / `interfaces` 模块不计入。原因是这些模块要么是装配代码、要么当前无测试（0 类匹配时 JaCoCo 会判为未达标而非跳过），强行纳入只会让全队 PR 被堵。

## 2. 当前覆盖率（2026-10-30 重测）

| 模块 | 行覆盖 | 覆盖行/总行 | 分支覆盖 | 门禁 | 状态 |
|---|---|---|---|---|---|
| `co-qa-domain`（核心聚合） | **100.0%** | 245 / 245 | 91.2% | 80% | ✅ |
| `co-identity-domain` | **82.3%** | 135 / 164 | 64.9% | 70% | ✅ |
| `co-discovery-domain` | **78.9%** | 75 / 95 | 65.7% | 70% | ✅ |
| `co-reputation-domain` | **77.2%** | 142 / 184 | 61.4% | 70% | ✅ |
| `co-qa-application` | 36.7% | 121 / 330 | 25.0% | 暂不纳入 | ⚠️ S4 待补 |
| `co-identity-application` | 27.7% | 59 / 213 | 14.7% | 暂不纳入 | ⚠️ S4 待补 |
| `co-identity-infrastructure` | 17.2% | 35 / 204 | 25.0% | 暂不纳入 | ⚠️ |
| `co-reputation-application` | 12.1% | 25 / 207 | 10.0% | 暂不纳入 | ⚠️ S4 待补 |
| `co-shared-kernel` | 9.2% | 6 / 65 | 13.2% | 暂不纳入 | ⚠️ 工具类为主 |
| `co-bootstrap` | 0.0% | 0 / 257 | 0.0% | 暂不纳入 | ⚠️ 装配代码 |

**领域层合计：597 / 688 ≈ 86.8%**（行），超过 Lab 7 的 70% 要求；4 个领域模块单独均达标。

单元测试：**16 个测试类（含 `@Nested` 内部类共 24 个测试报告）/ 89 个用例**，`mvn verify -DskipITs` 全绿，27 个模块 BUILD SUCCESS。

### 与上一版快照的差异（2026-10-27 → 2026-10-30）

| 模块 | 上版 | 本次 | 变化原因 |
|---|---|---|---|
| `co-qa-domain` | 82.9%（203/245） | **100.0%（245/245）** | PR #24 新增 `QaDomainContractTest`（265 行，覆盖 Question / Answer / Comment / Tag / Vote 五个契约），把剩余分支走全；上一版快照取的是该 PR 合并前的状态 |
| `co-identity-domain` | 75.7%（103/136） | 82.3%（135/164） | 期间该模块新增类，代码行数从 136 增至 164，覆盖率同步上升 |
| `co-reputation-domain` | 76.9%（140/182） | 77.2%（142/184） | #37 的迁移与并发修复带来少量新行 |
| `co-discovery-domain` | 78.9%（75/95） | 78.9%（75/95） | 未变 |

> 说明：核心聚合达到 100% 行覆盖，但**分支覆盖只有 91.2%**，仍有 9 个分支未覆盖
> （`Question` 3 个、`Comment` 5 个、`Answer` 1 个）。行覆盖满格不等于逻辑验证充分，
> 下一轮应针对这 9 个分支补用例，而不是看行数就认为可以收工。

## 3. 历史补齐记录（2026-10-27，PR #20）

> 以下为本节首次建立基线时做的事，保留作为过程记录；数字以第 2 节重测结果为准。

- `ReputationValueObjectTest`：覆盖此前零覆盖的 `ReputationReason`（标签 + `isVote()` 判定）、`LedgerEntry.of` 与两条空值守卫、`BadgeAward`，把 `co-reputation-domain` 从 68.1% 抬到 76.9%。
- 把 `domain.coverage.minimum` 从 `0`（空壳）改为 `0.70`，核心聚合单独 `0.80`。
- 新增 `backend/sonar-project.properties`，供 SonarQube 扫描时读取。

## 4. 如何本地生成报告

```bash
cd backend
mvn -B verify -DskipITs -Djacoco.skip=false
# HTML 报告：各模块 target/site/jacoco/index.html
# 汇总数据：各模块 target/site/jacoco/jacoco.csv
```

> ⚠️ **Windows 中文用户名会导致这一步失败**，报错形如
> `The forked VM terminated without properly saying goodbye`，且日志里的路径显示成乱码
> （如 `C:\Users\ÖìäÆ\`）。原因是 surefire 分叉出的 JVM 拿不到正确编码的 `-javaagent`
> 路径，加 `-Dfile.encoding=UTF-8` 也没用。
>
> 绕过办法：把仓库检出到**纯 ASCII 路径**再跑，例如
> `git worktree add C:/tmp/co-verify origin/main`，然后在那个目录下执行同样的命令。
> 2026-10-30 的重测就是这么跑的（27 个模块全部 SUCCESS）。
> **这是本机环境问题，不是代码问题——CI 在 ubuntu 上一直是通的。**

反向验证门禁是否真的生效（预期 BUILD FAILURE）：

```bash
mvn -B -f backend/pom.xml verify -pl co-reputation/co-reputation-domain -am \
    -DskipITs -Ddomain.coverage.minimum=0.99
# [WARNING] Rule violated for bundle co-reputation-domain: lines covered ratio is 0.77, but expected minimum is 0.99
# BUILD FAILURE
```

> ⚠️ 这条验证**不能拿 `co-qa-domain` 试**——它现在行覆盖 100%，把阈值调到 0.99 依然通过，
> 会让人误以为门禁没生效。要验证就得挑一个低于阈值的模块，例如上面的 reputation-domain（77.2%）。
> 2026-10-30 两种情形都实测过：qa-domain + 0.99 → BUILD SUCCESS（合理），
> reputation-domain + 0.99 → BUILD FAILURE（门禁确实在拦）。

## 5. 后续待办（S4）

1. **`co-qa-domain` 的分支覆盖 91.2% → 100%**：行覆盖已经满格，但 `Question` 有 3 个、
   `Comment` 有 5 个、`Answer` 有 1 个分支没走到。行数满了不代表逻辑验完了，这 9 个分支
   才是真正的缺口。
2. `co-qa-application` 36.7% → 70%：优先补 `AnswerCommandService` / `VoteService`。
3. `co-reputation-application` 12.1%、`co-identity-application` 27.7%：应用层整体偏低，
   但这两块目前不在门禁范围内，先保证领域层稳定。
4. SonarQube 服务端质量门禁需在有实例后配置，并作为 CI 必经步骤（见 Issue #39）。
