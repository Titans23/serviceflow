# 检索、重排与证据回答

RAG 解决文档知识问题，MySQL 提供商品和业务事实。两者在回答前汇合，但不能互相冒充权威来源。

## 检索链路

```text
原问题 + 本轮商品范围 + 文档类型 + 活动版本
  → Dense Top 20 / 中文 BM25 Top 20
  → RRF (k=60)，保留 Top 20
  → Reranker Top 5
  → Grader 按原问题判断证据充分性
  → 若不足，最多改写一次并重新检索
  → 模型选择事实编号 → 后端校验并拼接原文
```

[MilvusRagService](../../apps/serviceflow-server/src/main/java/com/serviceflow/rag/MilvusRagService.java) 负责检索、重排和充分性判定；[GroundedAnswerComposer](../../apps/serviceflow-server/src/main/java/com/serviceflow/ai/GroundedAnswerComposer.java) 负责最终事实编号协议。工作流决定查哪一类文档和哪个商品，检索服务不会自己扩大商品范围。

## 范围与版本先于相似度

每次检索固定 `documentType`、`productId` 和 MySQL 返回的活动 `documentVersionId`。显式切换型号或输入未知 SKU 时，工作流更新或清除旧商品记忆，防止上一轮商品进入事实池。

第一轮和改写后的第二轮共用范围与活动版本。改写可以改变检索词，Grader 始终回答原问题；否则模型可能通过换一个容易的问题来错误地判为“充分”。

## 为什么修正中文分词

新 Collection 使用 Milvus 内置 `chinese` analyzer。旧 `standard` 配置在这批中文资料上保留了过长的连续词项，削弱 BM25 的匹配效果。集合创建时声明分词器还不够：对已存在的集合也检查 analyzer，配置不符则明确报错。

现有标准分词集合不会被自动删除或重建。迁移时显式指定新的 `MILVUS_COLLECTION_NAME`，重新入库并验证，再切换使用；步骤见 [RAG v2 协议](../../quality/evaluation/rag-v2-protocol.md)。惰性初始化在并发锁内完成，全部步骤成功后才发布就绪标记。

## 各阶段失败时发生什么

| 阶段 | 处理 |
| --- | --- |
| 没有活动版本或候选 | 返回无证据，不用模型常识补齐参数 |
| Reranker 失败 | 保留融合排名前五，记录 degraded |
| Grader 失败 | 保留候选，判为不足并记录 degraded |
| 改写没有变化 | 直接使用首轮结果，不重复调用 |
| 改写或第二轮失败 | 保留首轮证据，标记不足和降级 |
| 后续阶段成功 | 不抹掉之前发生过的降级标记 |

默认业务路径启用混合检索、重排和有界改写；评测接口另外支持 BM25、DENSE、HYBRID、HYBRID_RERANK、HYBRID_RERANK_REWRITE，便于单独比较检索与后处理成本。BM25 基线不请求 Embedding。

## 为什么模型只返回事实编号

后端为目录事实和检索段落分配编号。模型最多选择 5 个编号，后端检查编号是否属于本轮上下文，再组织原始事实与来源，避免模型自由扩写参数或截掉使用条件。正文在选择完成后发送，因此界面的 token 事件不等同于逐 token 转发自由生成文本。

这只能约束回答内容的来源。选择了无关段落、漏掉必需条件、错误地判为不足，依然会导致回答不合格；原始资料本身的错误也不会自动纠正。评测必须同时检查检索、上下文覆盖和最终选择。

## 诊断与源码

[RagTraceRecorder](../../apps/serviceflow-server/src/main/java/com/serviceflow/rag/RagTraceRecorder.java) 保存每阶段 query、输入/输出分块、原因、充分性、降级和耗时。正文与完整证据仅在受控的本地诊断产物中保存；普通 SSE 的 `rag_evidence` 只提供分块编号与来源。

- [OpenAiCompatibleEmbeddingModel](../../apps/serviceflow-server/src/main/java/com/serviceflow/rag/OpenAiCompatibleEmbeddingModel.java)：Embedding 响应解析。
- [OpenAiRerankerClient](../../apps/serviceflow-server/src/main/java/com/serviceflow/rag/OpenAiRerankerClient.java)：百炼原生 Rerank 协议。
- [CloudAiGateway](../../apps/serviceflow-server/src/main/java/com/serviceflow/ai/CloudAiGateway.java)：Grader 与改写提示词。
- [RagRewriteDiagnosticsTest](../../apps/serviceflow-server/src/test/java/com/serviceflow/rag/RagRewriteDiagnosticsTest.java)：五种配置、范围约束、二轮原问题判定与降级。
- [CloudEvidenceSafetyTest](../../apps/serviceflow-server/src/test/java/com/serviceflow/ai/CloudEvidenceSafetyTest.java)：受约束回答的安全边界。
