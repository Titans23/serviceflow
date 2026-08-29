# ServiceFlow 详细技术设计与实现报告

## 1. 项目定位、目标与范围

### 1.1 技术选型与原因

ServiceFlow 被定位为一个“可运行的企业级电商智能客服模拟系统”，而不是通用聊天机器人。选定电商售前与售后场景，是因为它能在一个边界清晰的项目中同时展示三类能力：

- 商品、订单、支付和物流等确定性业务数据查询；
- 商品说明、退换货和保修政策等非结构化知识检索；
- 投诉建单、订单取消确认等需要状态和副作用控制的业务工作流。

项目采用模块化单体，而不是微服务。个人项目的访问规模不足以证明拆分服务带来的收益；模块化单体能保留清晰的领域边界、事务和部署简单性，同时仍能展示数据库、缓存、消息队列、向量检索、Agent 和前端工程能力。

### 1.2 实现范围

系统实现以下角色：

- `GUEST`：浏览、比较、商品咨询和政策咨询；
- `CUSTOMER`：增加正式会话、订单查询、取消确认和投诉建单；
- `ADMIN`：商品导入、知识库管理、工单处理和 AI 回答审计。

系统明确不实现个性化推荐、营销排序、优惠、实时库存、下单、支付、GraphRAG、多 Agent、NL2SQL 和模型微调。这些功能会引入新的事实源与责任边界，不属于当前客服闭环。

### 1.3 真实性边界

项目当前具备真实云模型调用、真实向量检索、真实数据库事务和真实消息消费，但运行在单机 Docker Compose 中，使用演示客户与演示订单。它适合用于简历、面试和系统设计答辩，不能被描述成已经承载生产用户或达到生产 SLA 的系统。

---

## 2. 总体架构

### 2.1 技术选型与原因

总体采用 Vue SPA + Spring Boot 模块化单体 + 多种专用基础设施：

- MySQL 负责强一致业务事实和审计；
- Redis 负责短生命周期状态、幂等锁和限流；
- RabbitMQ 隔离耗时的文档入库；
- Milvus 负责 Dense/BM25 混合检索；
- LangGraph4j 表达有状态客服路由；
- Prometheus 收集运行指标。

这种划分遵循“一个数据系统承担一种主要责任”。商品价格不会从向量文档读取，访客临时消息不会进入永久关系库，文档处理也不会阻塞上传接口。

### 2.2 运行架构

```text
Browser
  │
  │ REST + POST/SSE
  ▼
Nginx / Vue SPA :5173
  │
  ▼
Spring Boot :8080
  ├─ Spring Security / JWT
  ├─ ChatSessionService + ChatOrchestrator / LangGraph4j
  ├─ Product / Order / Ticket Services
  ├─ Knowledge Ingestion
  ├─ MyBatis → MySQL
  ├─ Redis Memory / Checkpoint / Limit / Lock
  ├─ RabbitMQ → DocumentConsumer
  ├─ Embedding / Reranker / Qwen
  └─ Milvus Hybrid Search

Prometheus :9090 ← /api/actuator/prometheus
```

### 2.3 请求主链路

一条聊天请求经过：

```text
ChatView
→ fetch POST + ReadableStream
→ ChatController
→ GUEST 限流
→ Virtual Thread
→ ChatSessionService / ChatRequestStore 会话归属与幂等检查
→ CustomerWorkflow
→ Intent Router
→ Product / Knowledge / Order / Complaint Branch
→ MySQL Tool 或 RAG
→ SSE meta/业务事件/token/done
→ 正式会话持久化与 AI 审计
```

---

## 3. Java 21、Spring Boot、Spring MVC 与 Virtual Threads

### 3.1 技术选型与原因

后端采用 Java 21 和 Spring Boot 3.5.16。Java 21 提供稳定的 Virtual Threads，适合客服系统中大量等待模型响应、数据库、Redis 和向量库的 I/O 请求。Spring MVC 的同步编程模型比 WebFlux 更容易与 MyBatis、Tika、RabbitMQ 和同步 HTTP 客户端组合。

没有选择 WebFlux，是因为当前依赖链中存在多处阻塞 I/O。强行混用 Reactor 与阻塞 SDK 会增加线程切换和调试成本，而不会自动获得端到端非阻塞收益。

### 3.2 实现过程

`application.yml` 设置：

```yaml
spring:
  threads.virtual.enabled: true
```

这使 Spring Boot 可将适合的请求处理工作放到虚拟线程上。对于 120 秒的 SSE 聊天请求，`ChatController` 还显式调用：

```java
Thread.startVirtualThread(() -> run(emitter, operation));
```

Controller 立即返回 `SseEmitter`，实际模型调用在虚拟线程继续运行。SSE 超时设置为 120 秒，确认操作为 60 秒。发生异常时统一发送 `error` 事件并完成连接。

DTO 主要使用 Java `record`，例如 `ChatModels.MessageRequest`、`OrderModels.OrderView` 和 `ServiceFlowProperties`。它们减少了可变状态和样板代码，并能直接映射 JSON 与 MyBatis 查询结果。

`ServiceFlowApplication` 通过 `@EnableConfigurationProperties` 注册类型安全配置；`ServiceFlowProperties` 将 JWT、模型、Embedding、Reranker、限流和 Milvus 参数映射为嵌套 record。

---

## 4. AI 模型协议与适配层

### 4.1 技术选型与原因

模型统一部署在阿里云百炼：

- Chat：`qwen-plus`；
- Embedding：`text-embedding-v4`，输出 1024 维向量；
- Reranker：`qwen3-rerank`。

Chat 与 Embedding 使用 OpenAI-compatible HTTP 格式。这样业务代码只依赖标准的请求/响应结构，未来更换兼容供应商时主要改变 URL、Key 和模型名。Reranker 目前使用百炼原生 `input + parameters` 请求体，因为重排接口并没有统一的 OpenAI 标准。

Chat 和 Embedding 已统一通过 Spring AI 1.1 的 `ChatModel`、`StreamingChatModel` 和 `EmbeddingModel` Bean 调用，模型名、Base URL、API Key、温度与超时由配置注入。Reranker 保留独立的百炼适配器，因为其请求体不是标准 Chat/Embedding 协议；这层边界让业务工作流不依赖供应商 SDK。

### 4.2 `AiGateway` 抽象

`AiGateway` 定义四种能力：

- `classify`：五分类意图识别；
- `streamAnswer`：流式回答；
- `evaluateEvidence`：证据充分性和可引用 Chunk 选择；
- `rewriteQuery`：一次查询改写。

同时定义 `EvidenceCandidate` 和 `EvidenceEvaluation`，使工作流只依赖领域接口，不依赖具体供应商响应。

### 4.3 `CloudAiGateway` 实现

`CloudAiGateway` 只在 `serviceflow.ai.mode=cloud` 时注册。Spring AI OpenAI starter 负责把结构化 `Prompt(SystemMessage + UserMessage)` 转换为 `/v1/chat/completions` 请求：

```json
{
  "model": "qwen-plus",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "temperature": 0.1,
  "stream": true
}
```

低温度用于降低分类和事实回答的随机性。`StreamingChatModel.stream(prompt)` 返回增量 `ChatResponse`，网关把每个 text delta 转换为现有 SSE `token` 事件；Spring AI 处理供应商 SSE 行协议，应用层只处理领域事件。

非流式调用用于分类、Grader 和 Rewrite。`AiCallExecutor` 统一施加连接/读取边界和指标，Resilience4j 只对未产生输出的网络异常、429 和 5xx 最多重试一次；流式首 token 后不重试，避免重复回答。

证据 Grader 被要求只返回：

```json
{"sufficient": true, "rankedChunkIds": ["..."]}
```

解析后还会用候选 Chunk ID 白名单过滤、去重并限制为 5 个，防止模型返回不存在的 ID。Markdown JSON 围栏会在反序列化前移除。

### 4.4 Demo 模式

`DemoAiGateway` 由条件配置在 `demo` 模式注册，使用关键词完成基础意图分类并返回固定范围文本。它的用途是让没有云 Key 的开发者验证页面和业务链路；云模式是当前完整演示路径。

---

## 5. 身份认证、授权与用户隔离

### 5.1 技术选型与原因

系统采用 Spring Security Resource Server + 自签发 HS256 JWT。JWT 适合前后端分离项目，服务端不需要额外保存登录 Session；Spring Security 能统一验证签名、过期时间和角色。

密码采用 BCrypt，原因是它自带随机盐且计算成本可调，适合账号密码存储。Token 放在 `sessionStorage` 而不是 `localStorage`，使浏览器标签页关闭后自动清理，符合演示系统的短会话目标。

### 5.2 Token 签发

`JwtService` 为游客生成 `guest:{UUID}` subject，角色为 `GUEST`，有效期 30 分钟。正式账号 subject 为 `app_user.id`，角色来自数据库，有效期 2 小时；客户 Token 额外携带 `customerId`。

JWT Secret 必须至少包含 32 个 UTF-8 字节。`SecurityConfig` 使用同一 HMAC Key 创建 Nimbus Encoder/Decoder，并由默认 Validator 检查标准时间声明。

`AuthService` 使用 `UserMapper.findByUsername` 读取账号，通过 `BCryptPasswordEncoder.matches` 校验密码、检查 `enabled`，成功后签发 Token。失败统一返回“用户名或密码错误”，不泄露账号是否存在。

### 5.3 RBAC

SecurityFilterChain 的规则为：

- `/auth/**`、健康、Prometheus、Swagger：公开；
- `/admin/**`：仅 ADMIN；
- `/orders/**`、`/tickets/**`：CUSTOMER 或 ADMIN 通过 URL 层；
- 商品与聊天：任何已认证身份，包括 GUEST。

`CurrentPrincipal.requireCustomerId()` 再执行领域层客户身份检查。因此 ADMIN 虽然能通过订单 URL 的角色规则，但没有绑定 customerId，不能冒充客户读取订单。

每条正式会话和订单查询都带 `customer_id` 条件：知道其他人的公开 ID 或订单号也无法读取其数据。

### 5.4 前端身份状态

Pinia `auth` Store 从 `sessionStorage` 恢复角色。`ensureGuest()` 在没有 Token 时调用 `/auth/guest`。登录保存 Token 与角色，退出清空整个 Session Storage。

Vue Router 对管理页面设置 `requiresAdmin`，非管理员被送往登录页并携带原始 redirect。管理员正常登录默认进入 `/admin/operations`，客户进入 `/chat`。

---

## 6. MySQL 数据模型、MyBatis 与 Flyway

### 6.1 技术选型与原因

MySQL 8.4 用作确定性事实源，因为商品、订单、支付、工单和审计需要事务、唯一约束、外键和条件更新。MyBatis XML 被选中而不是 ORM，是为了让订单乐观锁、幂等写入、JSON CAST、动态知识版本过滤等 SQL 明确可见，便于面试解释和性能分析。

Flyway 管理数据库演进，使任何新环境都能按 V1、V2、V3、V4 顺序得到相同 Schema 和演示数据。

### 6.2 V1 核心表

身份与客户：

- `customer`：业务客户；
- `app_user`：账号、BCrypt Hash、角色、客户绑定和启用状态。

商品：

- `product`：SKU、名称、品牌、型号、类别、JSON 规格、标价和销售状态；
- SKU 唯一；类别/销售状态和型号建立索引。

订单：

- `customer_order`：订单号、客户、状态、金额、version；
- `order_item`：商品、数量、购买单价；
- `payment`：支付与退款状态；
- `shipment_event`：按时间倒序的物流事件；
- `order_operation`：订单副作用幂等记录。

客服：

- `chat_session` 与 `chat_message`：只保存正式客户会话；
- `(session_id, client_request_id)` 唯一，防止同一请求产生两条正式回答；
- `ticket`：投诉/人工工单，`source_action_id` 唯一。

知识库：

- `knowledge_document`：逻辑文档和 `active_version_id`；
- `knowledge_document_version`：文件版本、存储路径、状态和错误。

### 6.3 后续迁移

V2 将最初的 ServiceFlow 示例商品替换成三款 HUAWEI Pura 80 系列数据，并同步修正演示订单金额。规格 JSON 保存来源 URL、核验日期和价格快照说明。

V3 增加 `ai_answer_audit`，并为工单增加处理人、处理备注、更新时间和状态时间索引。

V4 增加 `chat_request` 客户聊天幂等状态表和 `knowledge_ingest_outbox` 事务 Outbox。前者以 `(session_id, client_request_id)` 唯一键作为正式用户请求真相源；后者以 `document_version_id` 唯一键保证数据库提交与 RabbitMQ 发布之间可恢复。

### 6.4 MyBatis 映射方式

Mapper 接口只声明领域查询，SQL 放在 `resources/mapper/*.xml`。全局开启 `map-underscore-to-camel-case`，数据库的 `created_at` 可映射到 record 的 `createdAt`。

项目没有通用 BaseMapper 或无意义 Repository 包装。每个 XML 直接表达当前业务：

- Product XML：分页过滤、精确/模糊解析和 SKU Upsert；
- Order XML：客户隔离、操作预占、乐观取消和退款启动；
- Knowledge XML：版本状态条件更新和活动版本查询；
- Chat XML：正式会话与请求幂等结果；
- Ticket XML：状态条件更新；
- Audit XML：JSON 审计写入和后台关联查询。

---

## 7. 商品目录、商品解析与事实对比

### 7.1 技术选型与原因

商品价格、型号、规格和销售状态使用 MySQL JSON + 固定列组合：高频标识字段可索引，类别差异较大的规格放在 JSON 中。比较字段不由模型决定，而是使用 `product-comparison-fields.yml`，从而保证结果稳定、可审计、不会把不存在的字段补全。

### 7.2 商品查询

`ProductController` 提供分页搜索、详情和比较。`ProductService.search` 将负页码归零，把 page size 限制在 1–50，并同时执行列表与 count 查询。

`ProductModels.Product` 持有数据库返回的 `specsJson`，在 Service 层通过 Jackson 转换成 `Map<String,Object>` 后才返回 `ProductView`。JSON 解析失败被视为数据完整性异常，而不是静默返回空规格。

### 7.3 商品解析

SQL 解析顺序覆盖 SKU、型号、完整名称和名称包含，精确匹配优先。工作流还使用两个正则：

- `MODEL`：识别 `HUAWEI Pura 80 Pro`、`Pura 80 Ultra` 等型号；
- `SKU`：从自然语言中提取包含多个连字符的完整 SKU。

如果没有 pageContext，也没有提取到唯一商品，工作流返回 `product_selection_required` 候选事件。模型不会从多个结果中自行挑选。

### 7.4 页面上下文优先级

商品详情页把 `productId` 放入聊天请求的 `pageContext`。路由时，如果问题出现“这款、屏幕、充电、防水、适配”等商品指代，页面上下文会把误分类的知识问题修正为 `PRODUCT_QUERY`。订单和投诉意图不会被页面商品上下文覆盖。

### 7.5 商品比较

`ProductService.compare` 执行四层校验：

1. 必须选择 2–3 个不同 ID；
2. 所有 ID 必须存在；
3. 所有商品必须属于同一 category；
4. category 必须配置比较字段。

查询结果通过 ID Map 按用户请求顺序重新排列。比较 DTO 返回 category、字段白名单和原始商品事实；前端对缺失 Map Key 显示“暂无数据”。比较回答不进入模型自由生成，直接发送 `product_comparison` 结构化事件。

### 7.6 管理员商品导入

`ProductAdminController` 接收 `{products:[...]}` JSON。Service 限制每次 500 条，校验必填字段、长度、非负价格、销售状态和批次内 SKU 唯一性。SKU 统一 trim + uppercase。

MySQL 使用 `ON DUPLICATE KEY UPDATE`，因此同 SKU 重复导入会更新权威事实。整个批次由 `@Transactional` 包裹；任一商品失败，批次回滚。导入后按 SKU 重新读取并返回数据库中的最终记录。

---

## 8. LangGraph4j 客服工作流

### 8.1 技术选型与原因

LangGraph4j 用来表达“路由后进入不同业务分支”的状态化客服流程。相比把所有行为放在一个巨大 Prompt 中，显式 Graph 能保证订单查询只走订单服务，投诉只走工单服务，商品事实只走商品与 RAG 模块。

当前 Graph 保持最小结构：Router + 五个分支节点。Retrieve、Rerank、Grade 和 Rewrite 被封装在 `MilvusRagService` 内部，没有为了图形复杂度拆成更多节点。

### 8.2 Graph 构建

`CustomerWorkflow` 在构造时创建 `RedisSaver`，配置 Jackson State Serializer 和 30 分钟 TTL。Graph ID 为 `serviceflow-customer`。

节点：

```text
START → router
  ├─ CHAT → chat → END
  ├─ PRODUCT_QUERY → product → END
  ├─ KNOWLEDGE_QUERY → knowledge → END
  ├─ ORDER_QUERY → order → END
  └─ COMPLAINT → complaint → END
```

调用 `graph.invoke` 时以 sessionId 作为 threadId。执行后刷新 LangGraph Redis active key、thread key 和 checkpoints key 的 TTL；刷新失败只记录警告，不改变已经得到的业务结果。

### 8.3 Graph 状态

`WorkflowGraphState.SCHEMA` 包含：

- sessionId、subject、role、customerId；
- query、pageProductId、clientRequestId；
- intent、answer、citations、degraded、groundingContext；
- eventNames、eventPayloads。

业务分支使用 `ServiceFlowState` 作为运行时对象，包含 principal、query、productIds、activeOrderNo 和 retryCount。当前 active order 实际持久化在 `ChatMemory`，retryCount 尚未参与 RAG 循环，因为查询改写次数由 RAG Service 内部固定为一次。

### 8.4 意图路由

云模型首先从五个枚举中分类。之后执行确定性修正：

- 有商品页面上下文且问题是商品指代时，路由为商品；
- 模型误判为 CHAT，但检测到型号、页面商品或商品数据库匹配时，修正为商品；
- 订单和投诉优先级不被页面上下文覆盖。

这种“模型分类 + 业务规则校正”比完全依赖 LLM 更稳定。

### 8.5 分支结果与事件

每个分支返回 `Result(answer, citations, degraded, groundingContext)`。结构化事件先在 Graph State 中保存为名称与 JSON 字符串数组，Graph 完成后按顺序反序列化并发送 SSE。

---

## 9. 会话、Redis 记忆、幂等与限流

### 9.1 技术选型与原因

Redis 用于具有明确过期时间、无需永久保存且需要原子操作的数据：游客会话、最近消息、待确认动作、当前商品/订单、请求锁和限流计数。MySQL 则只保存正式客户的历史与审计。

### 9.2 访客会话

`ChatMemory.createGuestSession` 创建 UUID，会话标题保存为 String，session index 使用 ZSET 按创建时间排序。所有 key TTL 30 分钟。

消息使用 Redis List：

```text
chat:memory:{subject}:{sessionId}
```

每次追加后执行 `LTRIM -20 -1`，只保留最近 20 条并刷新 TTL。登录后不会合并游客历史，因为匿名身份无法可靠证明与正式客户是同一自然人。

### 9.3 正式会话

正式客户会话写入 `chat_session` 和 `chat_message`。任何读取先用 `public_id + customer_id` 找到内部数据库 ID，防止跨客户访问。为了给模型提供最近上下文，正式消息同时追加到 Redis 的短期记忆；永久历史仍以 MySQL 为准。

### 9.4 请求幂等

聊天请求必须携带合法 UUID `clientRequestId`。

处理顺序：

1. 先查已完成响应；游客查 Redis，客户查 MySQL assistant message；
2. 已完成则重放 `meta + token + done`，`replayed=true`；
3. 未完成则用 Redis `SET NX` 获取 2 分钟请求锁；
4. 锁已存在返回 `REQUEST_IN_PROGRESS`；
5. finally 删除锁。

游客的完成响应保存在 `chat:completed:{subject}:{session}:{request}` 30 分钟；正式回答依靠数据库唯一键 `(session_id, client_request_id)`。

### 9.5 待确认动作

订单取消和低置信度转人工先写入 10 分钟 `PendingAction`。确认接口再次校验 action subject 与 customerId，拒绝其他用户确认该动作。

用户拒绝时删除 action。确认订单取消时，actionId 同时作为订单操作 requestId；确认转人工时，actionId 作为 ticket source_action_id。因此即使重复确认，数据库幂等约束仍能阻止重复副作用。

### 9.6 GUEST 限流

`GuestRateLimiter` 对 subject 和 IP 分别维护固定窗口计数。Lua 脚本原子执行 `INCR`，首次请求设置 `PEXPIRE`。任一计数超过 20 即返回 HTTP 429。

这里实现的是首轮必要防滥用边界，不是完整网关级限流：没有滑动窗口、模型并发舱壁或分级套餐。

---

## 10. SSE 流式协议与回答生成

### 10.1 技术选型与原因

项目使用 POST + Fetch ReadableStream + Spring `SseEmitter`，而不是浏览器原生 `EventSource`。原因是聊天请求需要 JSON Body、Bearer Token、clientRequestId 和 pageContext；EventSource 原生只支持 GET 且 Header 能力有限。

### 10.2 后端事件

- `meta`：intent、citations、degraded；
- `token`：回答增量；
- `product_selection_required`：候选商品；
- `product_comparison`：结构化对比；
- `order`：订单卡片；
- `action_required`：二次确认；
- `ticket`：工单；
- `done`：messageId、引用、是否重放；
- `error`：稳定错误码和消息。

### 10.3 回答生成策略

`ChatOrchestrator.generateAnswer` 有三条明确路径：

1. CHAT：调用云模型流式生成，但 system prompt 限定客服范围；
2. 有可信 groundingContext 且网关支持 Grounded Generation：把最近 8 条上下文、当前问题、MySQL 事实和文档证据注入模型；
3. 订单、比较、投诉、无证据等确定性结果：按 28 字符切块模拟增量发送，不再让模型改写业务结果。

商品 system prompt 明确规定：结构化事实优先，文档仅说明功能/使用/适配/保修/注意事项；不编造库存、优惠、承诺或售后规则。

### 10.4 前端 SSE 解析

`api/client.ts` 使用 `response.body.getReader()` 和 `TextDecoder` 增量读取。缓冲区按双换行切分事件块，再解析 `event:` 与 `data:`。`token` 保持纯文本，其余事件尝试 JSON 解析。

`ChatView` 根据事件更新同一个 assistant message：追加 token、展示引用与 degraded 警告、候选按钮、比较表、订单卡片、确认卡片和工单 Toast。

首轮协议不支持 token 级断点续传；重新提交同一 clientRequestId 会重放最终消息。

---

## 11. Advanced RAG 检索链路

### 11.1 技术选型与原因

只用 Dense 向量检索可能漏掉 SKU、型号和政策术语；只用 BM25 又难以覆盖自然语言同义表达。因此项目使用 Milvus 原生 Dense + Sparse Hybrid Search，再使用 RRF 融合和云 Reranker 精排。

Embedding 与 Reranker 分开选择，是因为它们解决不同问题：Embedding 把文本映射到语义空间，Reranker 对 query-document 对做更精确的相关性判断。

### 11.2 Milvus Collection

`MilvusRagService.ensureCloudReady` 首次使用时检查 Collection，不存在则创建。Schema 包含：

- `chunkId` 主键；
- documentId、documentVersionId、documentType、productId；
- title、category、source、content；
- 1024 维 `denseVector`；
- `sparseVector`。

content 启用 analyzer，并配置 Milvus BM25 Function 从 content 生成 sparseVector。

索引参数：

- Dense：HNSW + COSINE，`M=16`、`efConstruction=200`；
- Sparse：`SPARSE_INVERTED_INDEX` + BM25；
- 查询时 HNSW `ef=64`。

### 11.3 Embedding

`OpenAiEmbeddingClient` 调用 `/embeddings`，请求 model 和单条 input，从 `data[0].embedding` 解析所有数值。空文本、空向量或非数值元素直接报错。

进入 Milvus 前，`MilvusRagService.embed` 强制检查向量维度为配置的 1024，防止更换模型后以错误维度写入 Collection。

### 11.4 活动版本过滤

搜索前先从 MySQL 查询当前 documentType 对应的 `active_version_id`。商品文档额外按 productIds 过滤。Milvus filter 只允许这些活动版本：失败版本和历史版本即使仍有向量，也不会参与回答。

### 11.5 Hybrid Search 与 RRF

每次搜索构造两个子请求：

- Dense：query embedding，Top20，COSINE；
- Sparse：原始 query，Top20，BM25。

Milvus 使用 RRF `k=60` 融合并输出 Top20。返回字段只取 chunkId、title、source、content，减少响应负载。

### 11.6 Reranker

`OpenAiRerankerClient` 把候选 content 作为 documents，设置 `top_n=5`、`return_documents=false`。它兼容从 `output.results` 或根 `results` 读取结果，按 index 映射回原 chunkId，也支持供应商直接返回 id。

结果按 relevance_score 降序、去重并截断。如果重排请求失败或返回无效结果，系统使用 RRF Top5，并把 `degraded=true`。

### 11.7 Grader 与 Query Rewrite

Top5 交给 `AiGateway.evaluateEvidence`。Grader 只允许选择实际候选 ID，并返回 sufficient。

若证据不足，RAG 调用 `rewriteQuery` 一次，再完整执行 Hybrid → Rerank → Grade。不会无限循环。Rewrite 失败时保留原证据但标记 insufficient 和 degraded。

Grader 本身不可用时，不把证据默认判定为充分：商品结构化事实仍可回答，政策问题进入证据不足/转人工流程，并设置 `degraded=true`。这样不会在 Grader 故障时扩大模型可回答范围。

### 11.8 商品与政策回答的差异

商品分支先取 MySQL facts，再以 `documentType=PRODUCT_MANUAL + productId` 检索文档。groundingContext 分成“结构化商品事实”和“文档证据”。引用使用知识文档的 source name。

政策分支只检索 `POLICY`。证据充分时直接把文档片段作为确定性回答；证据不足时，游客被提示登录，正式客户获得 `CREATE_TICKET` 确认卡片。

---

## 12. 知识文档上传、版本化和 RabbitMQ 入库

### 12.1 技术选型与原因

文档解析、分块、Embedding 和 Milvus 写入耗时且依赖外部服务，不应阻塞上传请求。因此元数据先写 MySQL，再通过 RabbitMQ 异步处理。原文件保存在 Compose 持久卷，MySQL 保存版本状态，Milvus 保存可检索 Chunk。

RabbitMQ 只承担文档入库，没有被扩展成所有日志的通用总线，保持职责单一。

### 12.2 上传校验

`KnowledgeService` 支持 `POLICY`、`PRODUCT_MANUAL`、`CUSTOMER_SERVICE_SOP`。商品说明必须关联存在的 productId。

文件边界：

- 只允许 MD、TXT、PDF；
- 最大 20 MB；
- 扩展名与 Tika MIME 双重校验；
- 原始文件名只保留 basename；
- 服务端生成 `{documentPublicId}/v{version}.{ext}` 路径；
- normalize 后必须仍位于 uploadRoot 下。

文件保存成功后在同一事务插入 PENDING 版本和 Outbox 行。事务提交后 `KnowledgeOutboxPublisher` 使用 Publisher Confirm 发布，成功才标记 PUBLISHED；数据库回滚时不会留下孤立消息，Broker 故障则由 Outbox 退避重试。

### 12.3 Queue 与 DLQ

`RabbitConfig` 创建 durable direct exchange、ingest queue 和 DLQ。主队列配置 dead-letter 到 DLQ；Spring Listener 设置不重新入队。消费异常最终进入 DLQ，同时版本状态写为 FAILED。

`KnowledgeOutboxPublisher` 每轮最多领取 50 条到期事件，使用 `FOR UPDATE SKIP LOCKED` 避免多实例重复抢占；Publisher Confirm 成功后标记 PUBLISHED，失败按有限指数退避并保留 `last_error`，超过上限由管理员重试。

### 12.4 幂等消费与状态机

Consumer 收到 versionId 后：

1. 版本不存在或 READY：直接返回；
2. `PENDING → PROCESSING` 条件更新失败：说明已被其他 Consumer 处理，返回；
3. Tika 解析，限制最多 2,000,000 字符；
4. 删除该 versionId 的旧向量；
5. 分块、Embedding 并逐块写入 Milvus；
6. 事务内将版本标记 READY，并更新 document.active_version_id。

发生异常时写 FAILED 和最多 500 字符错误，再抛出异常进入 DLQ。旧 active_version_id 没有被提前修改，因此失败的新版本不会影响线上检索。

管理员只能把 FAILED 版本重新置为 PENDING 并重新发消息。

### 12.5 Chunk 策略

当前实现按字符平铺：每块 2000 字符，重叠 320 字符，对应设计目标约 500 tokens / overlap 80，但代码没有使用 Tokenizer，因此报告应准确称为“字符近似切块”。每个 Chunk 使用独立 UUID，携带文档、版本、类型、商品、标题和来源元数据。

当前版本号通过 `MAX(version_no)+1` 生成。在单管理员演示场景足够；真正多管理员并发上传同一文档时，应增加行锁或重试唯一键冲突。

---

## 13. 订单查询、取消、退款与一致性

### 13.1 技术选型与原因

订单操作属于确定性业务，不交给 LLM 直接生成 SQL，也不通过自然语言猜测权限。工作流只负责识别订单号和决定调用哪个 Service，所有读写由固定 MyBatis SQL 完成。

取消订单同时使用事务、幂等表和乐观锁：

- 事务保证订单状态、退款状态和操作结果一起提交或回滚；
- request_id 唯一保证重复确认不重复副作用；
- version + status 条件更新解决并发状态覆盖。

### 13.2 查询

订单号正则为 `SF` 后至少 12 位数字。当前问题没有订单号时，从 Redis 读取会话 active order，实现“刚才那个订单”。

`OrderMapper.findOwned` 强制 `order_no + customer_id`。读取 Order 后，再按内部 orderId 查询 item、payment 和按时间倒序的 shipment events，组合为 `OrderView` 并发送 `order` SSE 事件。

### 13.3 二次确认

问题包含“取消”时，先调用 `canCancel`。不可取消直接返回退货提示；可取消则创建 `CANCEL_ORDER` PendingAction 并发送 `action_required`。

只有用户再次提交 `CONFIRM` 才执行取消。其他 decision 删除待确认动作并返回“已取消本次操作”。

### 13.4 幂等取消

`OrderService.cancel`：

1. 查 `order_operation.request_id`，存在则直接返回历史结果；
2. 验证订单属于客户；
3. `INSERT IGNORE` 预占 PROCESSING 操作记录；
4. 若预占失败，读取其他请求已创建的结果；
5. 非可取消状态记录 `NOT_CANCELLABLE`；
6. 条件 UPDATE 订单为 CANCELLED 并 version+1；
7. 更新行数为 0，记录 `CONFLICT`；
8. 成功后将已支付 payment 的 refund_status 设为 PROCESSING；
9. 完成操作记录为 CANCELLED。

条件 SQL 同时检查 `id、customer_id、version、status IN (...)`。因此客户隔离与乐观锁都发生在最终写入语句，而不只依赖前置查询。

---

## 14. 投诉工单与后台运营

### 14.1 技术选型与原因

投诉属于明确的人工服务请求，因此不需要二次确认，直接创建工单。低置信度普通咨询可能只是检索暂时不足，因此需要客户确认后再转人工。这两类入口最终复用同一个 Ticket Service。

工单状态使用受控有限状态机，而不是任意 status 更新，便于审计与解释。

### 14.2 创建幂等

投诉使用 chat clientRequestId 作为 source_action_id；确认转人工使用 actionId。`ticket.source_action_id` 唯一，SQL 使用 `INSERT IGNORE`。`createFromAction` 先查再插入，重复请求最后读取同一工单。

游客没有 customerId，不能创建工单，只收到登录提示。

### 14.3 状态机

`TicketService.TRANSITIONS` 明确定义：

```text
OPEN → ASSIGNED → RESOLVED → CLOSED
```

不能跳级、回退或更新 CLOSED。状态更新 SQL 带 `WHERE public_id=? AND status=currentStatus`，若两名管理员同时处理，只有一个成功，另一个获得 409 冲突。

首次处理通过 `COALESCE(assigned_to, operator)` 固定处理人。解决和关闭可更新最多 500 字的 resolutionNote。

### 14.4 运营中心

`OperationsView` 并行加载工单和最近 100 条 AI 审计。工单支持状态过滤、展开描述/处理备注和按当前状态显示下一动作。RESOLVED/CLOSED 操作要求输入处理结果。

AI 审计表展示客户、意图、模型、Prompt 版本、耗时、结果、降级和时间，展开后显示问题、最终回答、商品 ID 与引用 JSON。

---

## 15. AI 回答审计与可观测性

### 15.1 技术选型与原因

企业客服不仅要能回答，还要能解释“哪个模型、哪个 Prompt、用了什么证据、耗时多久”。MySQL 审计适合保存正式客户的可追溯记录；Micrometer + Prometheus 适合聚合指标；OpenTelemetry + Jaeger 用于跨 Controller、工作流、RAG 和外部调用定位延迟。

游客内容不进入审计表，以符合项目的匿名会话设计；但游客请求仍计入聚合指标。

### 15.2 审计实现

`AiAuditService.PROMPT_VERSION` 固定为 `customer-v1`。成功记录包括：

- session 与 clientRequestId；
- modelName、promptVersion、intent；
- productIds JSON、citations JSON；
- degraded、latencyMs、SUCCESS；
- question、answer。

失败记录 FAILED、异常类名和空 answer。审计表以 `(session_id, client_request_id)` 唯一，Mapper 使用 `INSERT IGNORE` 避免重复记录。

`ChatOrchestrator` 在获得请求幂等资格后记录 `System.nanoTime()`。回答持久化后写 success；运行时异常且尚未成功审计时写 failure。`audited` 标志防止回答已成功、随后 SSE 连接关闭导致重复记录失败。

### 15.3 指标

Micrometer 指标：

- `serviceflow.chat.requests`：按 intent、status 计数；
- `serviceflow.chat.degraded`：按 intent 计降级次数；
- `serviceflow.chat.duration`：按 intent、status 记录 Timer；
- SSE 当前连接数、模型/Embedding/Reranker/Milvus 超时与熔断；
- RAG 证据充分率、Rewrite 率、Reranker 降级率和无证据率；
- Chat 幂等命中、处理中冲突、Outbox 待发送/失败/滞留和 RabbitMQ 消费/DLQ；
- 订单乐观锁冲突与工单状态冲突。

Actuator 暴露 health、info 和 prometheus。Prometheus 每 15 秒抓取 `server:8080/api/actuator/prometheus`，TSDB 保留 7 天。

`docker compose --profile observability up -d` 会启动 Grafana 和 Jaeger，并自动加载 Prometheus 数据源与 ServiceFlow Dashboard。默认不在指标标签中放 customerId、sessionId 或问题全文，避免高基数和隐私泄露。模型健康检查只报告配置与熔断状态，不主动调用收费接口。

---

## 16. 前端架构与页面实现

### 16.1 技术选型与原因

Vue 3 Composition API + TypeScript 适合中小型管理与交互系统；Pinia 只保存全局身份状态，避免过度集中页面数据；Vue Router 管理公开与管理员页面；Element Plus 快速提供表单、表格、卡片、标签和确认弹窗。

Vite 提供快速开发构建，生产镜像使用 Nginx 托管静态资源并反向代理 `/api`。Nginx 关闭 API proxy buffering，避免 SSE token 被缓冲。

### 16.2 公共 API Client

`api()` 自动添加 JSON Content-Type 和 Bearer Token；FormData 上传不手工设置 Content-Type，让浏览器生成 multipart boundary。非 2xx 尝试读取后端 `{message}` 并抛出 Error。

`stream()` 实现 POST SSE 解析。它没有使用第三方 SSE 库，协议路径清晰且测试覆盖 JSON 与纯 token 数据。

### 16.3 页面

- `ProductsView`：自动获取 GUEST Token、搜索 SKU/型号/名称、类别筛选、详情和咨询入口；
- `ProductDetailView`：展示所有结构化 specs、标价、销售状态，并携带 productId 进入聊天；
- `CompareView`：最多勾选 3 个，同一类别外的 checkbox 禁用，展示事实对比表；
- `ChatView`：会话侧栏、消息流、候选商品、比较表、订单卡片、引用、降级警告、确认操作和工单提示；
- `LoginView`：客户/管理员登录，支持 redirect，管理员默认进入运营中心；
- `KnowledgeView`：上传文档、查看版本状态、重试失败版本；
- `ProductAdminView`：读取或粘贴 JSON，批量导入商品并显示结果；
- `OperationsView`：工单状态机和 AI 审计。

### 16.4 容器化前端

前端 Dockerfile 使用 Node 22 Alpine 执行 `npm ci` 和生产构建，再复制到 Nginx 1.27 Alpine。`try_files ... /index.html` 支持 Vue History Router 刷新；`proxy_read_timeout 130s` 高于后端聊天 SSE 的 120 秒。

当前主 JS Bundle 约 1.05 MB，Vite 会给出超过 500 kB 的警告。它不影响本地演示，但后续应使用路由懒加载和 manualChunks 分离 Element Plus。

---

## 17. Docker Compose 与交付

### 17.1 技术选型与原因

Docker Compose 适合个人项目复现完整中间件，避免要求面试官分别安装 MySQL、Redis、RabbitMQ、Milvus 和 Prometheus。服务都使用固定大版本或固定镜像标签，数据使用 named volume。

### 17.2 服务组成

- server：多阶段 Maven/JDK21 构建，运行阶段只使用 JRE21；
- web：Node 构建 + Nginx 运行；
- mysql 8.4；
- redis 7.4；
- rabbitmq 4 management；
- etcd + minio + milvus standalone；
- prometheus 3.5；
- Grafana 12 与 Jaeger 1.72（仅 `observability` profile）。

server 使用 uid 10001 的非 root 用户运行，上传目录授权给该用户。MySQL、Redis 和 RabbitMQ 定义健康检查；server 等待这些依赖健康后启动。Milvus 依赖 etcd 与 MinIO。

持久卷保存 MySQL、Redis、RabbitMQ、etcd、MinIO、Milvus、上传文件、Prometheus TSDB 和 Grafana 配置数据。

`.env` 负责注入密码、JWT Secret 和模型配置。Docker Compose 还配置 `host.docker.internal`，便于把模型 Base URL 指向宿主机本地兼容代理。

---

## 18. 测试与在线评测

### 18.1 技术选型与原因

测试分四层：

- JUnit 5 + Mockito：验证领域边界和并发语义；
- Testcontainers + WireMock：使用真实 MySQL、Redis、RabbitMQ 验证 Flyway、幂等和消息链路；
- Vitest + TypeScript Build：验证前端 SSE 基础与类型；
- Playwright：隔离的游客、登录、管理员路由与商品流程；
- PowerShell 在线评测：实际走 JWT、会话、SSE、云模型、RAG 和业务分支。

单元测试适合快速定位规则错误，在线评测用于发现协议编码、模型波动和端到端组合问题，两者不能互相替代。

### 18.2 后端单元与架构测试

`CustomerWorkflowIntentRoutingTest`：型号、SKU、页面上下文优先级、订单/投诉优先级和无关知识问题。

`CloudAiGatewayTest`：OpenAI-compatible 分类请求、SSE delta、缺失内容、一次传输重试、Grader ID 白名单。

`ProductServiceTest`：分页边界、详情/解析、请求顺序、缺失规格、跨类别、重复/超量、SKU 标准化导入和无效销售状态。

`OrderServiceTest`：幂等返回、支付订单取消退款、发货后拒绝、乐观锁冲突和客户隔离。

`OpenAiRerankerClientTest`：百炼原生请求体与 index → chunkId 映射。

`TicketServiceTest`：创建幂等、管理员过滤、合法前进、禁止跳级/回退、备注长度和并发更新冲突。

`ArchitectureTest`：Controller 不依赖 Mapper、Mapper 不依赖 Service、禁止字段注入。`ServiceFlowIntegrationIT` 使用 Testcontainers 启动 MySQL、Redis、RabbitMQ 并验证应用上下文；需要本机 Docker 引擎。

### 18.3 前端测试与构建

Vitest 验证 SSE JSON 事件和纯文本 token 解析；ESLint 检查 Vue/TypeScript；Playwright 覆盖游客商品流程和管理员路由守卫。`npm run build` 先执行 `vue-tsc --noEmit`，再执行 Vite production build，因此模板和 TypeScript 类型问题会阻止构建。

### 18.4 在线评测实现

`evaluation/serviceflow-eval-200.json` 包含 200 条脱敏用例：50 条商品说明、50 条售后政策、20 条商品详情、20 条商品比较、20 条订单与取消、20 条投诉/上下文/权限/异常。`scripts/validate-evaluation.ps1` 先校验字段、意图枚举和唯一 ID，并选择 20 条 Smoke；`run-evaluation.ps1` 默认使用该数据集。

`run-evaluation.ps1` 为每条用例创建独立 GUEST 会话，生成 UUID clientRequestId，调用实际 SSE 接口，解析 meta、done、事件名和 token 文本。

断言维度：

- expectedIntent、expectedProducts；
- expectedCitations；
- expectedEvents / expectedAbsentEvent；
- requiredFacts / forbiddenClaims；
- 是否收到 done，并统计 P50/P95、意图/引用/事件准确率。

脚本处理了 PowerShell 将无 charset SSE 误解为 Latin-1 的问题：先把 Content 按 Latin-1 取回原字节，再按 UTF-8 解码。关键事实比较前去掉空白和 Markdown 标记，避免 token 分片或 `**一年**` 造成误判。

输出同时包含机器可读 JSON 和 Markdown 汇总；任一失败时脚本退出码为 1，可接入 CI。

### 18.5 当前实测结果

- 后端：43 个单元/架构测试通过；`mvn -DskipITs verify` 的 Spotless、Enforcer、核心业务 JaCoCo 门禁通过；
- 前端：ESLint、2 个 Vitest、TypeScript 类型检查和 Vite 生产构建通过；
- 评测集：结构校验 200/200 通过；完整云评测、Testcontainers、Compose Playwright 和 k6 需要 Docker 引擎与模型配置可用后执行；
- 本报告不伪造尚未执行的 Recall/MRR/nDCG、幻觉率或 P95 数字。

---

## 19. 异常处理、安全边界与已知限制

### 19.1 技术选型与原因

REST 错误使用统一 JSON，SSE 错误使用 `error` 事件。领域代码优先抛 `ResponseStatusException` 表达 400/403/404/409/422/429；未知异常只对外返回“服务暂时不可用”，详细堆栈写日志。

### 19.2 已实现边界

- JWT 最小密钥长度；
- BCrypt 密码；
- 管理员 URL 权限；
- 客户数据 SQL 隔离；
- 文件大小、扩展名、MIME 与路径边界；
- 订单幂等和乐观锁；
- 工单状态条件更新；
- 访客 Token + IP 限流；
- Grader Chunk ID 白名单；
- 活动知识版本过滤；
- API Key 只从环境变量读取。

### 19.3 尚未实现

- HTTPS、WAF、CSRF 在 Cookie 模式下的防护；当前 Bearer Token API 关闭 CSRF；
- Secret Manager、密钥轮换和字段级加密；
- 更完整的全局成本预算、动态配额和多租户治理；
- 多实例下的文档 version_no 强并发生成；
- 自动清理失败版本已经写入的部分 Chunk；检索过滤能保证其不可见；
- 审计数据保留与删除策略；
- 完整 200 条云评测、Milvus 重量级集成和 k6 基准结果（脚本与 CI 已提供，需在本机执行）。

---

## 20. 全部源码职责索引

### 20.1 应用与配置

| 文件 | 职责 |
| --- | --- |
| `ServiceFlowApplication.java` | Spring Boot 入口，注册 ServiceFlowProperties |
| `ServiceFlowProperties.java` | JWT、模型、限流、上传和 Milvus 类型安全配置 |
| `SecurityConfig.java` | JWT Encoder/Decoder、BCrypt、RBAC、CORS |
| `ApiExceptionHandler.java` | REST 状态、校验与未知异常统一响应 |
| `RabbitConfig.java` | 文档 Exchange、Queue、DLQ 与 Binding |

### 20.2 身份

| 文件 | 职责 |
| --- | --- |
| `AuthController.java` | GUEST Token、登录和 `/me` |
| `AuthService.java` | 账号启用状态与 BCrypt 校验 |
| `CurrentPrincipal.java` | 从 JWT 提取角色/customerId，执行客户身份要求 |
| `JwtService.java` | 30 分钟 GUEST 与 2 小时用户 Token 签发 |
| `UserMapper.java` / `UserMapper.xml` | 按用户名读取账号 |

### 20.3 聊天与 Agent

| 文件 | 职责 |
| --- | --- |
| `AiGateway.java` | 分类、生成、Grader、Rewrite 能力接口 |
| `CloudAiGateway.java` | Spring AI ChatModel/StreamingChatModel Qwen 调用与领域 SSE 转换 |
| `DemoAiGateway.java` | 无云服务时的最小演示实现 |
| `ChatController.java` | 会话 REST、流式入口、确认入口和 SSE 错误 |
| `ChatSessionService.java` | 会话创建、列表、历史和归属校验 |
| `ChatOrchestrator.java` | 请求幂等、工作流执行、回答、持久化和审计 |
| `ChatActionService.java` | 订单取消/转人工确认及副作用事件 |
| `ChatRequestStore.java` | CUSTOMER MySQL 请求幂等状态机 |
| `SseEventWriter.java` | 统一 SSE 事件、稳定错误、连接生命周期和指标 |
| `AiCallExecutor.java` | 模型调用超时、虚拟线程和失败指标 |
| `ChatMemory.java` | Redis 游客会话、消息、完成结果、上下文和 PendingAction |
| `GuestRateLimiter.java` | Redis Lua Token/IP 固定窗口限流 |
| `ChatModels.java` | 请求、会话、消息和 SSE DTO |
| `ChatMapper.java` / `ChatMapper.xml` | 正式会话和消息持久化 |
| `Intent.java` | 五类意图枚举 |
| `ServiceFlowState.java` | 当前工作流运行时状态 |
| `CustomerWorkflow.java` | LangGraph、意图校正和五条业务分支 |

### 20.4 商品、订单、工单与审计

| 文件 | 职责 |
| --- | --- |
| `ProductController.java` | 商品列表、详情、比较 API |
| `ProductAdminController.java` | 管理员 JSON 批量导入 |
| `ProductService.java` | 分页、事实转换、比较校验、SKU Upsert |
| `ProductModels.java` | 商品领域 DTO |
| `ProductMapper.java` / `ProductMapper.xml` | 商品查询、解析、批量读回和 Upsert |
| `OrderController.java` | 客户订单详情 API |
| `OrderService.java` | 客户隔离、取消幂等、乐观锁和退款启动 |
| `OrderModels.java` | 订单、商品项、支付、物流与操作 DTO |
| `OrderMapper.java` / `OrderMapper.xml` | 订单确定性 SQL |
| `TicketController.java` | 客户工单列表 |
| `TicketAdminController.java` | 管理员工单列表和状态更新 |
| `TicketService.java` | 创建幂等与有限状态机 |
| `TicketMapper.java` / `TicketMapper.xml` | 工单查询和状态条件更新 |
| `AiAuditController.java` | 管理员最近审计查询 |
| `AiAuditService.java` | 成败审计和 Micrometer 指标 |
| `AiAuditMapper.java` / `AiAuditMapper.xml` | 审计写入及客户/会话关联查询 |

### 20.5 知识与 RAG

| 文件 | 职责 |
| --- | --- |
| `KnowledgeController.java` | 文档创建、新版本和列表 |
| `KnowledgeVersionController.java` | 失败版本重试 |
| `KnowledgeService.java` | 元数据/文件校验、持久化、Outbox 入队和激活 |
| `KnowledgeOutboxPublisher.java` | Outbox 锁定、Publisher Confirm、退避和指标 |
| `DocumentConsumer.java` | Tika 解析、字符切块、Embedding/Milvus 入库 |
| `KnowledgeMapper.java` / `KnowledgeMapper.xml` | 文档版本状态与活动版本 |
| `RagService.java` | Chunk、Evidence 与 SearchResult 接口 |
| `MilvusRagService.java` | Collection、Hybrid、RRF、Rerank、Grade、Rewrite 和降级 |
| `EmbeddingClient.java` | Embedding 接口 |
| `OpenAiEmbeddingClient.java` | Spring AI EmbeddingModel 适配 |
| `DemoEmbeddingClient.java` | 本地确定性 Embedding，供 Demo/测试使用 |
| `RerankerClient.java` | Reranker 接口 |
| `OpenAiRerankerClient.java` | 百炼 Reranker HTTP 适配与结果映射 |

### 20.6 前端

| 文件 | 职责 |
| --- | --- |
| `main.ts` | Vue、Pinia、Router、Element Plus 启动 |
| `App.vue` | 全局导航和角色菜单 |
| `router.ts` | 路由表和管理员守卫 |
| `api/client.ts` | REST 与 POST SSE Client |
| `stores/auth.ts` | Token/角色 Session Storage 状态 |
| `ProductsView.vue` | 商品列表和筛选 |
| `ProductDetailView.vue` | 商品结构化详情与咨询入口 |
| `CompareView.vue` | 同类别 2–3 商品比较 |
| `ChatView.vue` | 会话、流式消息和全部业务事件组件 |
| `LoginView.vue` | 登录和按角色跳转 |
| `KnowledgeView.vue` | 知识上传、状态和重试 |
| `ProductAdminView.vue` | 商品 JSON 导入 |
| `OperationsView.vue` | 工单运营和 AI 审计 |
| `styles.css` | 全局页面、聊天、表格与布局样式 |

### 20.7 数据、交付与验证

| 文件 | 职责 |
| --- | --- |
| `V1__initial_schema.sql` | 全部核心表、索引、约束和演示账号/订单 |
| `V2__replace_demo_catalog_with_huawei_pura80.sql` | 华为真实结构化商品快照 |
| `V3__add_operations_audit.sql` | AI 审计与工单运营字段 |
| `V4__reliability_baseline.sql` | Chat 幂等表与知识 Outbox |
| `product-comparison-fields.yml` | 类别比较字段白名单 |
| `application.yml` | 后端运行配置 |
| `serviceflow-server/pom.xml` | Java 21 编译、Spring Boot、LangGraph4j、MyBatis、Tika、测试与构建依赖 |
| `serviceflow-server/Dockerfile` | Maven/JDK 21 多阶段后端镜像构建 |
| `.env.example` | Compose 与模型环境变量模板 |
| `docker-compose.yml` | 九类运行服务与持久卷编排 |
| `serviceflow-web/package.json` | Vue、Element Plus、Pinia、Router、Vitest 与 Vite 依赖和脚本 |
| `serviceflow-web/vite.config.ts` | Vite 构建、测试与开发代理配置 |
| `serviceflow-web/Dockerfile` | Node 构建与 Nginx 运行时镜像 |
| `serviceflow-web/nginx.conf` | SPA 回退、静态资源和 `/api` 反向代理 |
| `monitoring/prometheus.yml` | Prometheus 抓取配置 |
| `observability/grafana/*` | Grafana 数据源、自动加载 Dashboard |
| `knowledge-source/huawei/*` | 官方来源登记和四份知识文档 |
| `evaluation/serviceflow-eval-200.json` | 200 条脱敏评测集 |
| `scripts/validate-evaluation.ps1` | 评测结构校验与 Smoke 选择 |
| `scripts/run-evaluation.ps1` | SSE 在线评测运行器与报告生成 |
| `performance/k6/serviceflow.js` | 商品、订单、Demo SSE 三类 k6 场景 |
| 后端 `src/test` | 43 个单元/架构测试与 Testcontainers IT |
| `client.test.ts` | 前端 SSE 数据解析测试 |
| `e2e/*.spec.ts` | Playwright 游客流程和路由守卫 |

---

## 21. 面试讲解建议

### 21.1 一分钟版本

“ServiceFlow 是一个售前和售后一体化的智能客服模拟系统。商品价格规格走 MySQL 确定性事实，功能说明与售后政策走 Milvus Dense/BM25/RRF/Rerank 的 Advanced RAG，LangGraph4j 根据五类意图选择商品、知识、订单或投诉分支。Redis 保存游客会话、Checkpoint、限流、请求锁和待确认动作，RabbitMQ 异步完成知识文档入库。订单取消通过 request_id 幂等表和 version 乐观锁控制，明确投诉自动建单。系统还记录正式回答审计并输出 Prometheus 指标，通过在线评测脚本实际验证意图、引用、SSE 事件和关键事实。”

### 21.2 可以深入追问的技术点

- 为什么商品结构化事实不能放进 RAG；
- 页面 productId 与模型意图冲突时如何确定优先级；
- Dense、BM25、RRF、Reranker 和 Grader 分别解决什么问题；
- 活动文档版本如何保证新版本失败不影响旧版本；
- 为什么 POST SSE 而不是 EventSource/WebSocket；
- 请求重放、处理中冲突和业务副作用幂等有什么区别；
- 订单 version 乐观锁和工单 status 条件更新如何避免覆盖；
- 为什么游客历史只在 Redis，为什么登录后不合并；
- Virtual Threads 解决了什么，不能解决什么；
- 当前项目距离真实生产还缺哪些可观测性、容量和安全能力。

### 21.3 诚实表达

可以说“实现并验证了”“模拟了企业场景”“接入真实云模型和公开官方数据”，不要说“支撑百万并发”“生产部署”“显著提升召回率”，除非后续完成相应压测或对照实验并保存报告。
