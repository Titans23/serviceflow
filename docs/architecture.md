# ServiceFlow 架构与请求时序

## 运行拓扑

```text
Vue 3 + Nginx
      │ REST / POST SSE
Spring Boot 3.5 (MVC + Virtual Threads)
  ├─ Auth / Product / Order / Ticket / Chat 应用服务
  ├─ CustomerWorkflow (LangGraph4j 确定性路由)
  ├─ Spring AI ChatModel/StreamingChatModel + EmbeddingModel
  ├─ Milvus Hybrid RAG + 百炼 Reranker 适配器
  ├─ MySQL 8.4 (事实、订单、正式会话、幂等、Outbox)
  ├─ Redis 7 (访客 Token 限流、访客记忆、Checkpoint)
  └─ RabbitMQ 4 (知识入库事件 + DLQ)

Prometheus ── Grafana（observability profile）
Jaeger <── OTLP Trace（observability profile）
```

## 一次聊天请求

1. `ChatController` 校验请求并获取当前主体，立即创建 `SseEmitter`。
2. CUSTOMER 请求由 `ChatRequestStore` 在 MySQL 原子插入 `PROCESSING`；完成请求直接重放助手消息，遗留请求超过 2 分钟才允许接管。GUEST 使用 Redis 30 分钟幂等记录。
3. `ChatOrchestrator` 调用 `CustomerWorkflow`。Router 将请求分为 CHAT、PRODUCT_QUERY、KNOWLEDGE_QUERY、ORDER_QUERY 或 COMPLAINT。
4. 产品请求先从 MySQL 读取结构化事实，再按 `productId` 和活动知识版本过滤 Milvus；比较事件仅使用类别白名单字段。
5. RAG 执行 Dense + BM25、RRF、Rerank 和一次 Grader/Rewrite。Reranker 失败保留 RRF Top5 并标记降级；无证据不调用模型常识补全。
6. `SseEventWriter` 统一发出 `meta`、`token`、结构化业务事件、`done` 或稳定错误码，并在关闭/超时/异常时递减连接指标。
7. 正式用户把用户消息、助手消息和请求状态在同一事务中完成；审计记录不包含完整问题、JWT 或 API Key。

## 知识入库时序

```text
上传 → MySQL document/version=PENDING + Outbox=PENDING（同一事务）
     → Publisher Confirm → RabbitMQ
     → Consumer 幂等解析/Tika/Chunk/Embedding/Milvus
     → READY → 原子切换 active_version_id
```

新版本失败不会覆盖旧活动版本。Outbox 发布失败进行有限退避，管理员可重试；重复消息由 `documentVersionId` 幂等保护。
