# 简历项目描述（数字以仓库报告为准）

**ServiceFlow｜售前产品咨询与售后客服平台（Java 21 / Spring AI / LangGraph4j）**

- 设计单体客服工作流：基于 LangGraph4j 实现五类意图路由、商品事实/RAG 证据分离、订单工具调用、投诉建单和 SSE 流式协议。
- 使用 Spring AI `ChatModel`、`StreamingChatModel`、`EmbeddingModel`，接入 OpenAI-compatible 云端模型；百炼 Reranker 通过独立适配器接入，并实现超时、Bulkhead、熔断和可解释降级。
- 以 MySQL 乐观锁和业务幂等记录保障订单取消；新增 `chat_request` 与事务 Outbox，解决重复提交、服务重启和数据库提交后消息丢失问题。
- 以 Testcontainers、WireMock、ArchUnit、Vitest、Playwright、k6 和 GitHub Actions 构建质量门禁；覆盖率、评测准确率和压测 P95 仅引用仓库实际生成的报告。
- 以 Prometheus、Grafana、Micrometer Tracing、OpenTelemetry 和 Jaeger 记录 SSE、RAG、模型调用、Outbox 与 JVM 指标，支持故障演练和链路定位。
