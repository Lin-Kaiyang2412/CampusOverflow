# SonarQube 扫描记录（2026-10-30）

验证对象：`backend` 全量源码的静态分析与覆盖率。
验证方式：在 GitHub Actions 的 `ubuntu-latest` runner 上现场起 `sonarqube:10.7-community` + `postgres:16`，扫完即销毁。
工作流：`.github/workflows/sonarqube.yml`。

## 为什么之前是空的

`backend/sonar-project.properties` 早就写好了（projectKey、排除项、质量门禁期望值都有），但：

- `deploy/docker-compose.yml` 的服务清单里**没有 sonarqube**（只有 mysql / redis / backend / web / prometheus / alertmanager / grafana）
- 团队也没有可用的 SonarQube 实例或 SonarCloud 账号

所以这份配置从落地起**一次都没执行过**，Lab 7 提交物 1 的 Dashboard 一直是空的（Issue #39 第 1 项）。

现在不需要外部账号：实例在 runner 上临时起，凭据是默认 admin，扫描完随容器一起销毁。

## 扫描结果

分析范围：**273 个源文件**，Java 21，JaCoCo 报告已导入。

Dashboard 截图见 [`sonarqube-2026-10-30-dashboard.png`](./sonarqube-2026-10-30-dashboard.png)（项目 `CampusOverflow / main`，8.3k 行代码，首次分析，46 个问题）。

| 指标 | 值 |
|---|---|
| ncloc（非注释代码行） | 8331 |
| 整体覆盖率 coverage | **33.6%**（分母 2.7k 行可覆盖代码） |
| 行覆盖率 line_coverage | 31.2% |
| 分支覆盖率 branch_coverage | 43.8% |
| Bugs | **2** |
| Vulnerabilities | 0 |
| Security Hotspots | **8**（E 级） |
| Code Smells | 44 |
| 重复率 duplicated_lines_density | 0.1% |
| 可靠性评级 | C（3.0） |
| 安全性评级 | A（1.0） |
| 可维护性评级 | A（1.0） |
| 技术债 sqale_index | 495 分钟 |

合计 46 个待处理问题：2 个可靠性、44 个可维护性，另有 8 个安全热点待复核。

## 关于「质量门禁」

这里有一个必须说清楚的地方，否则很容易误读。

服务端返回的门禁状态是 `OK`，但 `conditions` 是**空数组**：

```json
{"projectStatus":{"status":"OK","conditions":[],"ignoredConditions":false,"caycStatus":"compliant"}}
```

原因：默认的「Sonar way」门禁只卡**新代码**，而这是首次分析、没有任何 baseline，所以条件一条都不会被评估，状态直接给 `OK`。**这个 `OK` 不代表覆盖率达标。**

按 `sonar-project.properties` 里声明的阈值（覆盖率 > 70%、Bugs = 0、Vulnerabilities = 0）核对实际值：

| 条件 | 声明 | 实际 | 结论 |
|---|---|---|---|
| coverage | > 70 | 33.6 | **不通过** |
| bugs | = 0 | 2 | **不通过** |
| vulnerabilities | = 0 | 0 | 通过 |

所以工作流的汇总步骤直接拿指标与声明阈值比对，不再依赖服务端门禁状态。这也比改服务端配置更可靠——试过两次都不行：

- `/api/qualitygates/create` 返回体里没有 `id`（只回 `{"name":"CampusOverflow"}`）
- `/api/qualitygates/list` 返回的默认门禁也没有 `id`，且 `actions.manageConditions` 为 `false`，内置门禁不允许改条件

## 33.6% 与 JaCoCo 86.8% 为什么不一致

`docs/team/coverage-baseline.md` 里记录的是领域层合计 **86.8%**（597/688 行）。两个数字都对，统计口径不同：

- JaCoCo 的 `domain.coverage.minimum` 只卡**领域层**（`co-domain` 模块），那是纯 Java、易测的部分
- Sonar 统计的是**全部 8331 行**，包含 `api` / `application` / `infrastructure` / `interfaces` 各层，其中 `infrastructure`（持久化与适配器）和 `interfaces`（REST 层）基本没有单测覆盖

这正是 Sonar 比 JaCoCo 门禁更有价值的地方：JaCoCo 的门禁只看领域层，容易给出「覆盖率很好」的印象；Sonar 把整体拉出来，暴露的是真实缺口。

**结论：如果 Lab 7 要求整体覆盖率 > 70%，当前不达标，需要补 infrastructure / interfaces 层的测试，或者把门禁口径明确限定为领域层并在文档里写清楚。**

## 顺带发现

扫描过程中 Java 分析器报了 3 个解析错误，都指向同一类写法：

```
Unable to parse source file : 'co-discovery/.../AbstractJdbcSearchAdapter.java'
Parse error at line 52 column 31: A switch expression should have a default case
```

涉及：

- `co-discovery/.../infrastructure/search/AbstractJdbcSearchAdapter.java:52`
- `co-reputation/.../infrastructure/leaderboard/RedisLeaderboardStore.java:39`
- `co-reputation/.../domain/ReputationRulebook.java:43`

Sonar 的 ECJ 解析器要求 switch 表达式必须有 `default` 分支（Java 规范确实如此，除非枚举已穷尽）。这几处可能影响指标统计的完整性，值得业务侧确认是有意省略还是遗漏。

## 怎么复现

```bash
gh workflow run sonarqube.yml

# 本地等价操作（需 Docker）
docker network create sonar-net
docker run -d --name sonar-db --network sonar-net \
  -e POSTGRES_USER=sonar -e POSTGRES_PASSWORD=sonar -e POSTGRES_DB=sonar postgres:16-alpine
docker run -d --name sonarqube --network sonar-net -p 9000:9000 \
  -e SONAR_JDBC_URL=jdbc:postgresql://sonar-db:5432/sonar \
  -e SONAR_JDBC_USERNAME=sonar -e SONAR_JDBC_PASSWORD=sonar \
  -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true \
  -e SONAR_WEB_FORCEAUTHENTICATION=false \
  sonarqube:10.7-community
# 等 /api/system/status 返回 UP 后：
cd backend && mvn -B -DskipITs verify
mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar \
  -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token>
```

产物在运行的 artifact `sonarqube-evidence` 里（保留 30 天）：门禁与指标 JSON、扫描日志、Dashboard 截图。

## 踩过的坑

按遇到顺序：

1. **`vm.max_map_count` 太低**：runner 默认 65530，Elasticsearch 拒绝启动。需要 `sudo sysctl -w vm.max_map_count=262144`。
2. **`mvn sonar:sonar` 解析不到前缀**：Maven 的 `pluginGroups` 默认不含 `org.sonarsource.scanner.maven`，必须用完整插件坐标。
3. **等待就绪时 curl 报 exit 56**：SonarQube 启动初期 docker-proxy 会 accept 后立刻断开，而 GitHub Actions 默认 `bash -e`，`status=$(curl ...)` 一失败整个 step 就退出。每次探测都要容错。
4. **projectKey 被覆盖**：Maven 环境下插件忽略 `sonar-project.properties` 里的 `sonar.projectKey`，改用 pom 的 `groupId:artifactId`（实测是 `com.campusoverflow:campus-overflow`）。必须显式 `-Dsonar.projectKey`。
5. **采集抢跑**：报告上传后指标还在计算队列里，`qualitygate.wait=false` 时立刻采集会拿到空数组。要轮询 `/api/ce/component` 等队列清空。
6. **截图版本不匹配**：镜像自带 playwright 1.48.0，裸 `npx` 会拉最新版 1.64.1，浏览器对不上。钉住 `playwright@1.48.0`。
7. **截图拍到登录页**：`sonar.forceAuthentication` 默认为 true。加 `SONAR_WEB_FORCEAUTHENTICATION=false` 环境变量**无效**，它实际是存在数据库里的设置，必须走 `POST /api/settings/set?key=sonar.forceAuthentication&value=false`。配合项目转 public 后，匿名才能读到 dashboard。

## 截图里的 Quality Gate 显示 Passed，怎么理解

Dashboard 上写着 `Quality Gate: Passed`，但这个结论**不能用来证明覆盖率达标**——同一页 Coverage 只有 33.6%。

原因就是上一节说的：默认门禁只卡新代码，首次分析没有 baseline，条件不被评估，状态直接给 Passed。要看真实结论，以「按声明阈值核对」那张表为准。
