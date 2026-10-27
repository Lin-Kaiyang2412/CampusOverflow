# 前端本地联调记录 · 2026-10-27

执行人：组员 C。网页交互结果由执行人逐项操作并在会话中确认；监控采集另经本地 HTTP API 核对。本文不代表独立 QA 已复测。

## 通过项

- 注册、提问、第二账号回答、提问者采纳。
- 回答者收到采纳通知、全部已读、通知红点刷新。
- 搜索检索到问题，详情仍显示采纳状态。
- 对他人问题投票加分、再次点击取消；给自己投票不改分并显示错误（提示位置在页面底部，仍可优化）。
- 评论提交、刷新后保留、纯空白评论禁用提交。
- 浏览器窄窗口下列表和详情无明显截断或横向溢出；这不等于真机全覆盖。
- 仅一条问题时分页前后按钮禁用；跨页仍待验证。
- Prometheus 经 host.docker.internal:8080 采集 Mac 上的 Spring Boot，目标 UP、无采集错误，JVM 内存与请求率有数据。
- Grafana 截图确认可用性、请求率与平均响应时间显示数据。旧 backend:8080 的历史 0 不代表当前采集失败。5xx 无数据未单独验证。
- 执行人确认前端 Docker 镜像构建成功；http://localhost:8088 显示问题列表；进入详情后刷新正常。

## 本地运行方式

Mac 终端运行 Spring Boot；MySQL、Redis、Prometheus、Grafana 及验证用 Nginx 运行在 Docker Desktop。完整容器部署仍使用原配置，不以本次混合运行验收替代。

监控配置覆盖（仓库根目录执行）：

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.local-monitoring.yml --profile observability up -d --no-deps prometheus
```

验证前端镜像和 Nginx（独立 compose 项目，仅绑定本机 8088）：

```bash
docker compose -f deploy/docker-compose.local-web.yml up -d --build
```

访问 http://localhost:8088；后端需保持在 Mac 的 8080 端口运行。
`nginx.local.conf` 代理到 `host.docker.internal`，原 `nginx.conf` 的 `backend:8080` 保留用于完整容器部署。

## 待联合验收

1. 多页数据下翻页、筛选重置页码。
2. 教师/助教/管理员权限、普通用户越权拒绝。
3. 悬赏发布、积分不足、取消、到期及倒计时。
4. Grafana 其余业务面板、5xx 场景与告警；P95 需另验收，平均耗时不能替代。
5. 完整后端容器化部署、CI 最终状态、QA 复测与 Issue #11 关闭。

需要 B/D 提供角色账号、悬赏积分和分页数据。不把本记录中的待验证项声明为通过。
