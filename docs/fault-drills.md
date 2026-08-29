# 故障演练记录模板

这些演练在本地 Compose Demo 模式执行；表格中的结果必须由实际运行日志和指标填写，不预填成功数字。

| 演练 | 操作 | 预期行为 | 证据 |
| --- | --- | --- | --- |
| Reranker 不可用 | 停止/拦截 Reranker endpoint | 回退 RRF Top5，SSE `meta.degraded=true`，记录降级指标 | `serviceflow_rag_degraded_total`、traceId |
| RabbitMQ 暂时宕机 | 停止 rabbitmq 后上传文档，再恢复 | 版本保留 PENDING，Outbox 退避重试，恢复后只消费一次 | Outbox 表、RabbitMQ 日志 |
| 重复订单取消 | 相同 `requestId` 连续确认 | 只产生一次订单状态和退款更新，后续返回已完成结果 | `order_operation`、审计日志 |
| 请求处理中重启 | 在 SSE 首 token 前重启 server | CUSTOMER 相同请求 ID 超过 2 分钟可接管；未过期返回 `REQUEST_IN_PROGRESS` | `chat_request`、SSE error |

执行命令示例：

```bash
docker compose up -d
docker compose stop rabbitmq
docker compose start rabbitmq
docker compose --profile observability up -d
```
