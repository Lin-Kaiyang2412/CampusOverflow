# 覆盖率基线与质量门禁（Lab 7）

> 维护人：组员D（Developer + QA）
> 数据来源：`mvn -B -f backend/pom.xml verify -DskipITs` 生成的 JaCoCo 报告
> 快照时间：2026-10-27（基于 PR #20 的实现状态）

## 1. 门禁规则

| 范围 | 指标 | 阈值 | 配置位置 |
|---|---|---|---|
| 领域层（`com/campusoverflow/*/domain/**`） | 行覆盖率 | **0.70** | `backend/pom.xml` → `domain.coverage.minimum` |
| 核心聚合 `co-qa-domain` | 行覆盖率 | **0.80** | `backend/co-qa/co-qa-domain/pom.xml` 覆盖属性 |
| 全部模块（SonarQube 服务端） | Bugs / Vulnerabilities | **0** | SonarQube Quality Gate |
| 全部模块（SonarQube 服务端） | 覆盖率 | **70%** | SonarQube Quality Gate |

门禁在 `verify` 阶段执行，未达标直接中断构建（`jacoco:check`），因此 CI 的「覆盖率门禁」步骤会自动生效。

> 只对 4 个领域模块启用：`co-shared-kernel`、`co-bootstrap`、各 `application` / `infrastructure` / `interfaces` 模块不计入。原因是这些模块要么是装配代码、要么当前无测试（0 类匹配时 JaCoCo 会判为未达标而非跳过），强行纳入只会让全队 PR 被堵。

## 2. 当前覆盖率（行覆盖率）

| 模块 | 覆盖率 | 覆盖行/总行 | 门禁 | 状态 |
|---|---|---|---|---|
| `co-qa-domain`（核心聚合） | **82.9%** | 203 / 245 | 80% | ✅ |
| `co-discovery-domain` | **78.9%** | 75 / 95 | 70% | ✅ |
| `co-reputation-domain` | **76.9%** | 140 / 182 | 70% | ✅ |
| `co-identity-domain` | **75.7%** | 103 / 136 | 70% | ✅ |
| `co-qa-application` | 36.7% | 121 / 330 | 暂不纳入 | ⚠️ S4 待补 |
| `co-shared-kernel` | 10.0% | 6 / 60 | 暂不纳入 | ⚠️ 工具类为主 |
| `co-bootstrap` | 0.0% | 0 / 257 | 暂不纳入 | ⚠️ 装配代码 |

领域层合计：521 / 658 ≈ **79.2%**，已超过 Lab 7 的 70% 要求。

## 3. 本轮补齐内容

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

反向验证门禁是否真的生效（预期 BUILD FAILURE）：

```bash
mvn -B -f backend/pom.xml verify -pl co-qa/co-qa-domain -am \
    -DskipITs -Ddomain.coverage.minimum=0.99
# [WARNING] Rule violated for bundle co-qa-domain: lines covered ratio is 0.82, but expected minimum is 0.99
```

## 5. 后续待办（S4）

1. `co-qa-application` 36.7% → 优先补 `AnswerCommandService` / `VoteService` 的分支，目标 70%。
2. `co-qa-domain` 82.9% → 90%（Lab 7 对核心聚合的要求），缺口主要在 `Question` 的编辑/删除分支。
3. SonarQube 服务端质量门禁需在有实例后配置，并作为 CI 必经步骤（随 Issue #13 一起做）。
