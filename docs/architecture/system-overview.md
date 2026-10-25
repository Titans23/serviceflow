# ServiceFlow 架构与请求时序

## 运行拓扑

```text
Vue 3 + Nginx
      │ REST / POST SSE
Spring Boot 3.5 (MVC + Virtual Threads)
  ├─ Auth / Product / Order / Ticket / Chat 应用服务
  ├─ CustomerWorkflow (LangGraph4j 确定性路由)
  ├─ Spring AI StreamingChatModel + EmbeddingModel接口
  ├─ Milvus Hybrid RAG + 百炼 Reranker 适配器
  ├─ MySQL 8.4 (事实、订单、正式会话、幂等、Outbox)
  ├─ Redis 7 (限流、近期记忆、当前商品/订单、待确认动作、Checkpoint)
  └─ RabbitMQ 4 (知识入库事件 + DLQ)

Prometheus ── Grafana（observability profile）
Jaeger <── OTLP Trace（observability profile）
```

## 一次聊天请求

1. `ChatController` 校验请求并获取当前主体，立即创建 `SseEmitter`。
2. CUSTOMER 请求由 `ChatRequestStore` 在 MySQL 原子插入 `PROCESSING`；完成请求直接重放助手消息，遗留请求超过 2 分钟才允许接管。GUEST 使用 Redis 30 分钟幂等记录。
3. `ChatController.stream`将实际工作交给虚拟线程；`ChatServiceImpl`调用`CustomerWorkflow`。Router结合模型分类与业务规则选择CHAT、PRODUCT_QUERY、KNOWLEDGE_QUERY、ORDER_QUERY或COMPLAINT。RedisSaver按sessionId记录图状态，但不负责业务副作用的可靠重放。
4. 产品请求先从 MySQL 读取结构化事实，再按 `productId` 和活动知识版本过滤 Milvus；比较事件仅使用类别白名单字段。
   商品页提示仅绑定入口会话，首次商品响应后不再逐轮覆盖记忆；手动选择历史会话会清除入口提示。页面与记忆商品都经过本轮明确SKU/型号的切换判断，未知SKU清除旧引用，避免旧商品范围进入检索与事实池。
5. RAG执行Dense/BM25各Top20、RRF融合、Reranker Top5和Grader；不足时至多改写一次并重检。两轮固定商品/文档类型/活动版本，Grader均判原问题。Reranker失败保留RRF Top5，Grader失败保留候选但判不足，累计降级不会被后轮成功清除；无证据不使用模型常识补全。

商品事实回答采用受约束的证据选择：云模型返回本轮事实编号，后端校验编号后输出目录事实或完整检索段落，阻止自由扩写参数和使用条件。选择完成后再发送正文 token，因而不再逐 token 转发模型自由生成。事实选择是否相关、是否完整仍需要评测；原始资料错误也不会被这种机制自动纠正。`rag_evidence` SSE 元数据仅提供分块 ID 和来源，不包含原文。
6. `SseEventWriter` 统一发出 `meta`、`token`、结构化业务事件、`done` 或稳定错误码，并在关闭/超时/异常时递减连接指标。
7. 客户请求的begin事务登记请求与用户消息，complete事务登记助手消息与完成状态；两者之间包含外部模型调用，不是一笔长事务。正式客户审计另行写入，当前实际包含完整问题/回答，不能宣称已自动脱敏；公共报告只保留脱敏结果，不提交原始记录、JWT或API Key。

## 确认执行与未知结果

`CustomerWorkflow.order`只生成绑定客户、会话和订单的PendingAction；`ChatActionServiceImpl.confirm`只从服务端动作取参数并复核身份。`OrderServiceImpl.cancel`先查归属，再查历史操作；唯一键冲突后使用`FOR SHARE`当前读，避免MySQL REPEATABLE READ旧快照漏掉已提交结果。条件更新订单、设置支付退款状态、创建等额唯一退款请求及完成账本在同一事务中提交。

确认响应缺失时，浏览器通过`GET /chat/actions/{id}/result`查本人持久账本与订单，缓存过期也可核验已提交结果；404保持未知，不自动重发确认。退款沙箱按eventId/attemptNo/金额及终态约束回调与重试。sessionStorage只是身份分区的关联线索，Checkpoint不是退款执行凭证。

## 知识入库时序

```text
上传 → MySQL document/version=PENDING + Outbox=PENDING（同一事务）
     → Publisher Confirm → RabbitMQ
     → Consumer 幂等解析/Tika/Chunk/Embedding/Milvus
     → READY → 原子切换 active_version_id
```

新版本普通处理失败不会切换旧活动指针。发布器在数据库事务内`SKIP LOCKED`领取到期事件并等待Confirm，失败有限退避；Confirm后数据库提交失败仍可能重复投递。消费者通过PENDING→PROCESSING领取、READY短路和确定性Chunk ID处理普通重投，不能称跨存储exactly-once。

当前没有PROCESSING崩溃自动接管，也没有阻止旧版本晚完成后回切活动指针的单调条件；同文档并发上传版本分配/文件路径也未完整保证。文件、MySQL和Milvus并非一个事务。已有资料导入和活动版本过滤证据支持正常发布路径，不能扩写为所有崩溃窗口可恢复。具体代码与取舍见[工程证据表](../portfolio/resume-project.md#从实现追问设计)。
