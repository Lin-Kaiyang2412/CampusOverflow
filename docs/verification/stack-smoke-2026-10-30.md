# 编排冒烟验证记录（2026-10-30）

验证对象：`deploy/docker-compose.yml` 描述的完整容器栈（mysql + redis + backend + web）。
验证方式：在 GitHub Actions 的 `ubuntu-latest` runner 上现场构建镜像并 `docker compose up -d --wait`。
工作流：`.github/workflows/stack-smoke.yml`，运行 [41020014064](https://github.com/Lin-Kaiyang2412/CampusOverflow/actions/runs/41020014064)，提交 `4dfd398`。

## 为什么用 runner 而不是本地

团队没有可 SSH 的服务器，仓库变量 `DEPLOY_ENABLED` 一直没配，`ci.yml` 的 `deploy` job 从建仓至今从未执行过（见 #39）。而 GitHub 的 runner 自带 Docker，可以在它上面把编排完整跑一遍，拿到与本地 `docker compose up` 等价的结果。

**这份记录能证明什么、不能证明什么**：

- 能证明：镜像可构建、依赖启动顺序正确、四个服务都能变 healthy、后端 readiness 通过、Web 能访问、指标端点可用。
- 不能证明：线上可访问。Lab 8 若要求一个对外 URL，仍需 `deploy` job 配齐 `DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_SSH_KEY` / `DEPLOY_URL` 并有一台目标服务器。

## 结论

| 检查项 | 期望 | 实际 |
|---|---|---|
| 后端 `/actuator/health` | 200 | **200** |
| 前端首页 | 200 | **200** |
| 运行中容器数 | 4 | **4**（全部 healthy） |

```
{"status":"UP","groups":["liveness","readiness"]}      # /actuator/health
{"status":"UP"}                                        # /actuator/health/readiness
```

```
NAME         IMAGE                           STATUS                    PORTS
co-backend   campus-overflow-backend:local   Up (healthy)              0.0.0.0:8080->8080/tcp
co-web       campus-overflow-web:local       Up (healthy)              0.0.0.0:80->80/tcp
co-mysql     mysql:8.0.39                    Up (healthy)              0.0.0.0:3306->3306/tcp
co-redis     redis:7-alpine                  Up (healthy)              0.0.0.0:6379->6379/tcp
```

镜像内容核对（`app.jar` 75.5 MiB，前端 `index.html` + `assets/` 都在）：

```
/app
-rw-r--r-- 1 root root 79127247 Oct 30 07:26 app.jar

/usr/share/nginx/html
drwxr-xr-x assets
-rw-r--r-- index.html
-rw-r--r-- 50x.html
```

`/actuator/prometheus` 返回 40 行指标，监控链路端点可用。

## 过程中的三轮运行

### 第一轮 `41018814749`：栈起不来

```
co-backend  exited (1)
logs/backend.log:  co-backend  | Error: Unable to access jarfile app.jar
```

同时暴露了工作流自身的一个 bug：`docker compose up ... | tee` 的退出码取的是 `tee` 的，compose 失败时该步骤仍显示 success。已加 `set -o pipefail`。

### 第二轮 `41019319690`：定位到镜像里没有 jar

新增「诊断镜像内容（仅失败时）」步骤，进镜像列目录，得到直接证据——`/app` 是空目录：

```
/app
total 8
drwxr-xr-x 2 root root 4096 Oct 30 06:39 .
drwxr-xr-x 1 root root 4096 Oct 30 07:19 ..

Config: ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]  WorkingDir: /app  User: app
```

镜像配置全对，就是没有 jar。

### 根因

翻 `ci.yml` 里 `images` job 的构建日志（run `41015050283`）：

```
#16 [INFO] Building jar: /src/co-bootstrap/target/campus-overflow.jar
#16 [INFO] The original artifact has been renamed to ...campus-overflow.jar.original
#16 [INFO] BUILD SUCCESS
#17 [stage-1 4/4] COPY --from=build /src/co-bootstrap/target/co-bootstrap-*.jar app.jar
#17 DONE 0.0s
```

`backend/co-bootstrap/pom.xml:138` 设了 `<finalName>campus-overflow</finalName>`，Maven 产物叫 `campus-overflow.jar`；而 Dockerfile 找的是 `co-bootstrap-*.jar`，**glob 一个都没匹配上**。

关键在于 **BuildKit 对 `COPY --from` 的空 glob 不报错**，而是静默产出一个空层。GHCR 上那个 latest 的 manifest 也印证了这一点：COPY 那层只有 32 字节（`sha256:4f4fb700...`，Docker 空 tar.gz 的经典 digest），整个镜像 8 层里没有任何一层是 jar 的体量。

于是构建显示 success、镜像照常推上 GHCR、`deploy` job 如果真跑了也会拉到一个必然起不来的镜像。**这个 bug 从镜像第一次构建就存在，只是在此之前没人真正启动过它。**

### 第三轮 `41020014064`：修复后通过

改动见 PR #45：

- `backend.Dockerfile` 的 COPY 改用 `campus-overflow.jar`，并在 build 阶段与 final 阶段各加 `test` 断言，让同类问题变成构建期失败而非运行期事故
- `frontend.Dockerfile` 同样加断言（它 `COPY --from=build /src/dist` 复制目录，有一模一样的静默空层风险）
- 冒烟改为默认现场构建镜像：`ci.yml` 的 `images` job 是 `if: github.event_name == 'push'`，PR 里改 Dockerfile 不会更新 latest，拉 latest 等于在验旧代码
- 新增「校验镜像内容」步骤，启动前先证明 `app.jar` / `index.html` 真的在镜像里

## 怎么复现

```bash
# 手动触发（需要验 GHCR 成品镜像时填 image_tag，留空则现场构建）
gh workflow run stack-smoke.yml

# 本地等价操作
docker compose -f deploy/docker-compose.yml up -d --wait --wait-timeout 300
curl -s http://localhost:8080/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
docker compose -f deploy/docker-compose.yml down -v
```

产物在运行的 artifact `stack-smoke-evidence` 里，保留 30 天。
