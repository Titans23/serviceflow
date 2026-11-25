# 聊天请求与会话

沿一次 POST SSE 请求看身份、幂等、工作流和响应如何衔接。

## 一次聊天请求

1. `ChatController` 校验请求并获取当前主体，立即创建 `SseEmitter`。
2. CUSTOMER 请求由 `ChatRequestStore` 在 MySQL 登记为 `PROCESSING`。失败或超过 2 分钟的处理记录可接管，完成与失败更新均需匹配当前执行标识。GUEST 在同一 Redis 脚本内检查完成结果并领取 2 分钟的锁，提交时验证标识并原子保存历史和 30 分钟完成记录。
3. `ChatController.stream`将实际工作交给虚拟线程；`ChatServiceImpl`调用`CustomerWorkflow`。Router结合模型分类与业务规则选择CHAT、PRODUCT_QUERY、KNOWLEDGE_QUERY、ORDER_QUERY或COMPLAINT。RedisSaver按sessionId记录图状态，但不负责业务副作用的可靠重放。
4. 产品请求先从 MySQL 读取结构化事实，再按 `productId` 和活动知识版本过滤 Milvus；比较事件仅使用类别白名单字段。
   商品页提示仅绑定入口会话，首次商品响应后不再逐轮覆盖记忆；手动选择历史会话会清除入口提示。页面与记忆商品都经过本轮明确SKU/型号的切换判断，未知SKU清除旧引用，避免旧商品范围进入检索与事实池。
5. RAG执行Dense/BM25各Top20、RRF融合、Reranker Top5和Grader；不足时至多改写一次并重检。两轮固定商品/文档类型/活动版本，Grader均判原问题。Reranker失败保留RRF Top5，Grader失败保留候选但判不足，累计降级不会被后轮成功清除；无证据不使用模型常识补全。

商品事实回答采用受约束的证据选择：云模型返回本轮事实编号，后端校验编号后输出目录事实或完整检索段落，阻止自由扩写参数和使用条件。选择完成后再发送正文 token，因而不再逐 token 转发模型自由生成。事实选择是否相关、是否完整仍需要评测；原始资料错误也不会被这种机制自动纠正。`rag_evidence` SSE 元数据仅提供分块 ID 和来源，不包含原文。
6. `SseEventWriter` 统一发出 `progress`、`meta`、`token`、结构化业务事件、`done` 或稳定错误码，并在关闭/超时/异常时递减连接指标。工作流执行前发送处理进度；首段非空正文产生时记录 `serviceflow.chat.first_token_server`，口径是服务端处理起点到正文产生，不包含缓存重放，也不代表浏览器收到数据的时间。
7. 客户请求的 begin 事务登记请求与用户消息，complete 事务登记助手消息与完成状态；两者之间包含外部模型调用，不是一笔长事务。独立 AI 审计默认不复制问题/回答正文，读取接口默认隐藏历史正文；`SERVICEFLOW_AUDIT_RECORD_CONTENT=true` 可显式开启。原聊天历史与旧审计数据均保留，不能宣称已删除或全面匿名化。公共报告只保留脱敏结果，不提交原始记录、JWT 或 API Key。

## 普通聊天断线恢复

前端以 `done` 确认本轮收全。读流失败或提前结束时，保留正文并显示「重试本轮」。点击后向原 SSE 端点提交同一会话、同一 `clientRequestId` 和完整的原请求体。已完成的请求重放原消息编号、正文和业务卡片，处理中保留原气泡，失败的请求领取新执行标识后重新执行。

重试入口保存在当前聊天页，适用于当前会话的最新一轮。用户可以直接发新一轮，旧气泡保留中断提示。刷新后可以从历史查看已保存的消息。订单取消确认仍使用独立的动作编号和只读核验接口。

确定性业务结果先保存，再发送正文和卡片。流式模型回答在生成完成后保存，然后发送 `done`。升级时先执行 V16 迁移，新代码接管流量前让旧版聊天流结束，确保所有执行者都按执行标识提交和清理。

## 多商品比较

商品路由一次提取有序目标列表，MySQL 逐项核对 SKU、型号、容量和地区。需要澄清时，`comparison_selection_required` 带已确认商品和逐项候选。比如要比较 10 款、已确认 8 款，用户可补充其余两款，也可先比较已确认的 8 款。

聊天支持比较 2–10 款同类别商品，独立比较页支持 2–3 款。待比较列表在 Redis 按主体与会话保存 30 分钟。通过 `comparisonSelection` 选择候选、补充型号或请求先比较已确认的商品。部分比较后仍可继续补充，候选与对比表都支持历史展示和同请求重放。

模型不可用时，规则按明确列出的商品逐项匹配目录。查不准的商品保留为待确认，已确认的商品可以继续比较。

## 源码阅读入口

- [ChatController](../../apps/serviceflow-server/src/main/java/com/serviceflow/controller/ChatController.java)
- [ChatServiceImpl](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatServiceImpl.java)
- [ChatRequestStore](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/support/ChatRequestStore.java)
- [CustomerWorkflow](../../apps/serviceflow-server/src/main/java/com/serviceflow/agent/CustomerWorkflow.java)
- [ProductComparisonConversation](../../apps/serviceflow-server/src/main/java/com/serviceflow/agent/ProductComparisonConversation.java)
- [SseEventWriter](../../apps/serviceflow-server/src/main/java/com/serviceflow/web/sse/SseEventWriter.java)
