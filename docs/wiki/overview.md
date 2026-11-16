# ServiceFlow 架构导读

ServiceFlow 是一个有业务状态的 AI 客服与售后系统。模型负责理解问题、选择证据和提出查询计划；订单归属、取消确认、退款金额和最终状态由后端验证并持久化。

## 从哪条链路开始

| 想了解什么 | 阅读路径 | 重点 |
| --- | --- | --- |
| Agent 如何接入业务 | [聊天请求](chat.md) → [订单规划](orders.md) → [取消确认](confirmation.md) | 模型输出的边界、权限、事务和结果恢复 |
| RAG 如何提供可追溯回答 | [知识入库](knowledge.md) → [检索与回答](rag.md) → [评测](evaluation.md) | 活动版本、中文检索、事实编号、逐阶段指标 |
| 后端如何处理不确定结果 | [异步退款](refund.md) → [工单与退货](after-sales.md) | 数据库任务、租约、查询优先、人工接手 |
| 如何运行和验证 | [运行与排查](operations.md) → [验证与开发](verification.md) | Demo / Cloud 边界、隔离环境、真实证据 |

## 系统组成

```text
Vue 3 / Nginx
     │ REST + POST SSE
Spring Boot / MVC + Virtual Threads
     ├─ CustomerWorkflow / LangGraph4j
     │    ├─ 商品事实 / 订单查询 / 确认 / 工单
     │    └─ Milvus → Reranker → Grader → 事实编号选择
     ├─ MySQL：业务事实、正式会话、动作决定、任务和账本
     ├─ Redis：访客会话、近期记忆、候选订单、Checkpoint
     └─ RabbitMQ：知识入库事件、退款任务通知
             └─ 独立退款模拟渠道 / HTTP + SQLite
```

Java 21、Spring Boot、MyBatis 和 Flyway 构成后端；Vue 3、TypeScript 和 Pinia 构成前端。模型通过 Spring AI 接口和百炼协议适配接入。具体依赖版本以 [pom.xml](../../apps/serviceflow-server/pom.xml) 和 [package.json](../../apps/serviceflow-web/package.json) 为准。

## 各组件保存什么

| 组件 | 保存的内容 | 不能替代什么 |
| --- | --- | --- |
| MySQL | 订单、确认结果、退款请求、Outbox、正式会话和审计 | 不能直接与文件系统或 Milvus 做一个数据库事务 |
| Redis | 有期限的上下文、候选列表、图 Checkpoint、访客限流 | 不能授权退款，也不是持久业务执行凭证 |
| RabbitMQ | 提醒消费者处理某个持久任务 | 消息被确认不等于退款或入库已完成 |
| Milvus | 文档版本对应的向量、稀疏索引和原文分块 | 不是价格、订单状态和支付结果的权威来源 |
| 模型 | 分类、查询计划、充分性判定、事实编号选择 | 不直接修改数据库，不自行决定退款金额 |

## 源码分层

从 [ChatController](../../apps/serviceflow-server/src/main/java/com/serviceflow/controller/ChatController.java) 开始，依次读应用服务、工作流、业务服务和 Mapper。Agent/RAG 不绕过业务 Service 直接写订单。

- `controller/`：请求校验、身份获取和 REST/SSE 协议。
- `service/impl/` 与 `service/ActionDecisionService` 等：业务编排、事务、授权和状态转换。
- `agent/`：意图路由、商品上下文和订单查询计划执行。
- `ai/`、`rag/`：模型协议、证据选择、检索与诊断。
- `infrastructure/`：Redis 记忆、消息投递、任务消费者。
- `mapper/`、[XML](../../apps/serviceflow-server/src/main/resources/mapper/) 与 [迁移](../../apps/serviceflow-server/src/main/resources/db/migration/)：SQL、锁和唯一约束。

## 实现范围

项目提供商品咨询、本人订单查询、取消确认、异步退款、工单协作和单件全额退货。退款渠道是独立模拟服务；公开商品资料与合成订单用于验证。当前不是通用 Agent Harness，也没有真实支付结算、多商户或无限工具循环。

旧技术报告和阶段验收保留在 [历史文档索引](../archive/README.md)。Wiki 描述当前代码，历史报告只证明其标注版本上的受测行为。
