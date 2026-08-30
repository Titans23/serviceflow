# 故障演练记录（2026-08-30）

以下演练均在本地 Docker Compose 环境实际执行。Reranker 演练使用 Cloud 模式，其余演练使用 Demo 模式，避免对收费模型做并发调用。

| 演练 | 实际操作 | 实际结果 | 证据 |
| --- | --- | --- | --- |
| Reranker 不可用 | 将百炼 Reranker 请求置于无效参数状态，保留 Embedding、Milvus、Grader 和 Chat 可用 | 百炼返回 HTTP 400，服务回退到 RRF Top5；请求仍返回引用与 `done`，`meta.degraded=true` | 日志 `Reranker unavailable; using RRF top 5`；`serviceflow_rag_reranker_degraded_total=2` |
| RabbitMQ 暂时宕机 | 停止 RabbitMQ，为保修政策创建版本 9，然后恢复 Broker | 停机时 `version=PENDING, active=3, outbox=PENDING`；恢复后自动变为 `READY, active=9, PUBLISHED`，旧版本在新版本完成前持续可见 | `knowledge_document_version`、`knowledge_document.active_version_id`、`knowledge_ingest_outbox` 联表查询 |
| 重复订单取消 | 对订单 `SF202608280003` 的同一 action 连续发送两次 `CONFIRM` | 两次均返回同一 `CANCELLED`；最终订单 `CANCELLED`、退款 `PROCESSING`，`order_operation` 仅 1 条 | action 两次 SSE `done` 完全一致；数据库聚合计数为 1 |
| 请求处理中重启 | 原子写入一条 3 分钟前的 CUSTOMER `PROCESSING` 与 USER 消息，重启 server，再以同一 `clientRequestId` 请求 | 请求被接管并返回 `meta/token/done`；最终 `COMPLETED`，USER 与 ASSISTANT 各 1 条 | `chat_request` 与 `chat_message` 聚合结果 `COMPLETED|1|1` |

复现时可使用：

```bash
docker compose up -d
docker compose stop rabbitmq
docker compose start rabbitmq
docker compose --profile observability up -d
```
