# ServiceFlow

ServiceFlow 是面向作品集的电商智能客服项目，覆盖售前商品咨询、订单取消与退款沙箱、人工工单运营。云模式接入阿里云百炼 Qwen Chat、`text-embedding-v4` 和 `qwen3-rerank`，使用 MySQL 业务事实、Milvus 混合检索、Redis 会话状态、RabbitMQ 异步知识入库，通过 SSE 向 Vue 前端返回结构化事件与回答增量。项目没有真实商户交易、支付通道或生产用户流量。

> 完整设计与全部源码实现说明见 [技术报告](docs/technical-report.md)，文档入口见 [docs/README.md](docs/README.md)。

当前展示主线是**业务Agent的授权、确认、幂等执行与故障恢复**。先看[约5分钟演示脚本](docs/guides/demo-script.md)，按[隔离运行手册](docs/guides/evaluation-and-refund.md)准备真实业务服务；演示不需要付费模型。

| 当前证据 | 结果与边界 |
| --- | --- |
| [取消→退款与丢响应恢复](quality/evaluation/results/recovery-validation-2026-10-25.md) | 31项检查；提交后丢响应、丢动作缓存、server重启后仍可查本人结果，操作和等额退款各1条。退款为管理员沙箱，无真实资金。 |
| [刷新与身份隔离](quality/evaluation/results/recovery-refresh-validation-2026-10-25.md) | 13项浏览器验收，区分7个真实后端恢复场景与6个mock界面场景。未知结果只读核验，不自动重发确认；不保证跨设备恢复。 |
| [真实云评测与语义复核](quality/evaluation/results/cloud-final-evaluation-2026-10-25.md) | 历史开发集175/175、首次华为留出23/25；新来源首次留出12/14、已见失败回归1/2。规则成绩不是全量语义正确率，屏幕反问题仍待真实云诊断。 |
| [RAG诊断与请求预算](quality/evaluation/results/provider-budget-validation-2026-10-25.md) | 阶段trace和3例诊断已准备；本地HTTP证明27次provider上限、失败计数、第28次发送前拒绝。新云验证尚未执行。 |

公开资料入口：[原始23 SKU数据包](data/retail-v1/README.md)、[数据与评测口径](quality/evaluation/README.md)。后续取舍见[求职路线](docs/project-assessment-2026-10-23.md)；历史结果保留在各阶段报告，不覆盖首次留出成绩。

## 1. 核心能力

### 售前商品服务

- 游客无需注册即可浏览、搜索和咨询商品。
- 商品页通过 `pageContext.productId` 将页面上下文传入客服，优先于自然语言猜测。
- 支持从自然语言中识别 SKU、型号和规范化商品名；同名多结果返回候选卡片，不让模型自行猜测。
- 支持 2–3 个同类别商品比较；比较字段由版本化 YAML 白名单控制。
- 价格、型号、规格和销售状态只读取 MySQL，RAG 文档不能覆盖结构化事实。
- 缺失规格显示“暂无数据”，不由模型补齐，也不输出推荐排名。

### 售后业务服务

- 客户登录后可以查询订单、支付、退款和物流事件。
- 支持“刚才那个订单”等会话内订单指代。
- 取消订单需要二次确认；`CREATED / PAID / PROCESSING` 可取消，发货后拒绝并提示退货政策。
- 订单取消使用业务幂等记录、客户隔离和 `id + customer_id + version + status` 条件更新。
- 已付款取消在同一事务创建唯一退款请求；支持失败原因、重试、重复/迟到/冲突回调处理。管理员沙箱默认关闭，不转移真实资金；客户可在订单页刷新退款进度。
- 确认响应缺少终止done时保留待核验标识，只读查询本人操作账本和当前订单状态；404保持未知，不自动重发取消。
- 同标签刷新或返回客服页可继续只读核验；退出/换账号清理标识，陈旧标识与回包不能跨身份展示。
- 明确投诉自动创建工单；普通问题证据不足时先询问是否转人工。
- 管理员工单按 `OPEN → ASSIGNED → RESOLVED → CLOSED` 单向流转。

### 智能问答与 RAG

- 五类意图：`CHAT`、`PRODUCT_QUERY`、`KNOWLEDGE_QUERY`、`ORDER_QUERY`、`COMPLAINT`。
- LangGraph4j 负责路由和状态化工作流，Redis Saver 保存 30 分钟 Checkpoint。
- 框架提供状态图与Checkpoint，项目实现路由和工具执行边界；订单事务结果由MySQL账本核验，Checkpoint不等于安全业务重放。
- 商品回答组合 MySQL 结构化事实与 `PRODUCT_MANUAL` 文档证据。
- 政策回答只检索 `POLICY` 活动版本。
- Milvus 执行 Dense Top20 + BM25 Top20 + RRF(k=60)，云 Reranker 取 Top5，再由结构化 Grader 判断证据充分性。
- 证据不足时最多改写查询一次；Reranker 或 Grader 故障会标记 `degraded=true`。
- 无证据时禁止凭模型常识编造商品能力、适配性或售后规则。

### 工程与评测

- GUEST、CUSTOMER、ADMIN 三类身份与 JWT RBAC。
- 访客 Token 30 分钟，正式用户 Token 2 小时。
- 访客会话、最近 20 条消息和幂等结果只保存在 Redis，不写 MySQL、不在登录后合并。
- 访客按 Token 与 IP 分别执行“10 分钟最多 20 次”Redis Lua 原子限流。
- 正式客户回答记录模型、Prompt 版本、意图、商品 ID、引用、降级状态、耗时、问题和答案。
- Micrometer 暴露聊天请求量、意图、成功/失败、SSE 连接、降级、模型调用、幂等和 Outbox 指标，Prometheus 每 15 秒抓取。
- Grafana 和 Jaeger 通过 `observability` profile 按需启动，自动加载数据源、面板和 OTLP 链路。
- v2 评测包含 200 个项目编写场景、30 个多轮会话和独立的 10 场景退款专项；来源族分组、指标适用分母、真实业务状态读回与待人工复核的语义结果分开记录。

## 2. 技术栈

| 层次 | 技术 | 当前版本/用途 |
| --- | --- | --- |
| 后端语言 | Java | 21，record、Virtual Threads |
| Web 框架 | Spring Boot / Spring MVC | 3.5.16，REST、SSE、Validation、Actuator |
| AI 协议 | OpenAI-compatible + DashScope Rerank | Qwen Chat、Embedding、Reranker |
| Agent 工作流 | LangGraph4j | 1.8.24，状态图与 Redis Checkpoint |
| 数据访问 | 原生 MyBatis XML | 显式 SQL、条件更新、动态过滤 |
| 关系数据库 | MySQL | 8.4，业务事实、事务、审计和版本元数据 |
| 缓存/状态 | Redis | 7.4，会话、记忆、Checkpoint、限流、锁和待确认操作 |
| 消息队列 | RabbitMQ | 4，文档异步入库和 DLQ |
| 向量数据库 | Milvus | 2.6.22，HNSW、BM25 和 Hybrid Search |
| 文档解析 | Apache Tika | 3.2.3，MD/TXT/PDF |
| 前端 | Vue / TypeScript / Vite | Vue 3.5、TypeScript 5.9、Vite 7 |
| UI/状态/路由 | Element Plus / Pinia / Vue Router | 管理台、身份状态和页面权限 |
| 可观测性 | Micrometer / Prometheus / Grafana / Jaeger | 指标、Dashboard、OTLP Trace |
| 稳定性 | Resilience4j | 超时、Retry、Bulkhead、Circuit Breaker |
| 质量保障 | Testcontainers / WireMock / ArchUnit / Playwright / k6 | 集成、架构、E2E 与性能证据 |
| 交付 | Docker Compose / Nginx | 一键拉起完整依赖和 SPA 反向代理 |

为什么采用 Spring MVC + Virtual Threads 而没有使用 WebFlux：客服请求主要等待模型、MySQL、Redis 和 Milvus 等外部 I/O。Virtual Threads 能保持同步 Java 调用链的可读性，同时避免每条长连接长期占用一个昂贵的平台线程，更适合这个个人项目的复杂度与展示目标。

## 3. 系统架构

```text
Vue 3 SPA
   │ REST + POST/SSE
   ▼
Nginx :5173
   │ /api 反向代理
   ▼
Spring Boot :8080 ───────────────► Prometheus :9090
   │
   ├─ Security/JWT ── GUEST / CUSTOMER / ADMIN
   ├─ Service 接口 + service.impl 实现 ─ 会话、幂等、工作流、SSE、审计
   ├─ LangGraph4j ─── Intent Router
   │    ├─ Product ── MySQL Facts + Product Manual RAG
   │    ├─ Knowledge ─ Policy RAG + Rewrite
   │    ├─ Order ──── MySQL Tool + 二次确认
   │    └─ Complaint ─ Ticket
   ├─ Redis ───────── Memory / Checkpoint / Rate Limit / Action
   ├─ MySQL ───────── 业务事实 / 正式会话 / 审计
   └─ RabbitMQ ────── 文档版本异步入库
          │
          ▼
       Tika → Chunk → Embedding → Milvus
                                Dense + BM25 + RRF
                                        │
                                    Reranker/Grader
```

## 4. 目录结构

```text
ServiceFlow/
├─ apps/
│  ├─ serviceflow-server/       Spring Boot 后端应用
│  │  ├─ src/main/java/com/serviceflow/
│  │  │  ├─ controller/        REST/SSE 协议入口
│  │  │  ├─ service/           业务服务接口
│  │  │  ├─ service/impl/      业务服务实现
│  │  │  ├─ mapper/            MyBatis Mapper 接口
│  │  │  ├─ model/             请求、响应与领域数据模型
│  │  │  ├─ agent|ai|rag/      Agent 编排、模型适配与混合检索
│  │  │  ├─ infrastructure/    Redis 会话与 RabbitMQ 入库设施
│  │  │  ├─ security|web/      JWT 身份与 SSE 输出
│  │  │  └─ config|exception/  横切配置与统一异常
│  │  ├─ src/main/resources/   Flyway、MyBatis XML、业务配置
│  │  └─ src/test/             单元、架构与集成测试
│  └─ serviceflow-web/          Vue 3 前端应用与 Playwright
├─ data/knowledge/              可公开、可追溯的知识样本
├─ deploy/observability/        Prometheus 与 Grafana 配置
├─ docs/                        架构、ADR、运行手册和技术报告
├─ quality/
│  ├─ evaluation/              数据集、在线评测和脱敏结果
│  └─ performance/             k6 场景与基准结果
├─ scripts/
│  ├─ evaluation/              评测校验、执行与审计脚本
│  └─ verification/            Milvus 等环境验收脚本
├─ docker-compose.yml
└─ README.md
```

后端采用国内 Java 项目常见的分层包结构：Controller 只负责协议转换，Service 接口定义业务能力，`service.impl` 承担事务与编排，Mapper 专注 MyBatis 数据访问，Model 不依赖 Web 层。Agent、RAG、Redis 和消息组件保留独立包，避免把基础设施伪装成普通业务 Service。

## 5. 快速启动

### 5.1 前置条件

- Docker Desktop，包含 Docker Compose。
- 业务执行与恢复演示使用隔离Demo模式，不需要API Key；真实云问答才需要阿里云百炼配置与数据/费用授权。

求职展示直接使用[五分钟脚本的准备步骤](docs/guides/demo-script.md)，地址为15173/18080。以下5.2–5.5是主Compose的通用开发启动，地址为5173/8080，默认Demo回答不能证明真实RAG质量。

### 5.2 配置环境变量

```powershell
Copy-Item .env.example .env
```

云模式的关键配置：

```dotenv
SERVICEFLOW_AI_MODE=cloud
SERVICEFLOW_RAG_MODE=cloud

OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
OPENAI_API_KEY=your-dashscope-api-key
OPENAI_MODEL=qwen-plus

OPENAI_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
OPENAI_EMBEDDING_API_KEY=
OPENAI_EMBEDDING_MODEL=text-embedding-v4

OPENAI_RERANKER_BASE_URL=https://dashscope.aliyuncs.com/api/v1
OPENAI_RERANKER_API_KEY=
OPENAI_RERANKER_MODEL=qwen3-rerank
OPENAI_RERANKER_PATH=/services/rerank/text-rerank/text-rerank
```

`OPENAI_*` 表示协议适配层，不代表项目同时依赖两家模型供应商。Chat 和 Embedding 使用 OpenAI-compatible 请求格式；Reranker 使用百炼原生请求结构。Embedding/Reranker Key 留空时会继承 `OPENAI_API_KEY`。

不要提交真实 `.env`。部署到公共环境前必须替换默认数据库密码、RabbitMQ 密码和至少 32 字节的 JWT Secret。

### 5.3 启动

```powershell
docker compose up --build -d
docker compose ps
```

首次启动会创建持久卷并执行 Flyway 迁移；Milvus Collection 会在首次知识入库或检索时惰性初始化。知识文档需要在管理员页面上传；仓库中的 `data/knowledge/huawei` 提供了可演示文档。

### 5.4 访问地址

| 服务 | 地址 |
| --- | --- |
| Web | http://localhost:5173 |
| 运营中心 | http://localhost:5173/admin/operations |
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| 健康检查 | http://localhost:8080/api/actuator/health |
| Prometheus 指标 | http://localhost:8080/api/actuator/prometheus |
| Prometheus UI | http://localhost:9090 |
| Grafana（observability） | http://localhost:3000 |
| Jaeger（observability） | http://localhost:16686 |
| RabbitMQ UI | http://localhost:15672 |

演示账号：

- 客户：`customer / Customer123!`
- 管理员：`admin / Admin123!`

演示账号只适用于本地项目。

### 5.5 启动可观测性组件

```powershell
$env:TRACING_SAMPLING_PROBABILITY = '1.0'
docker compose --profile observability up -d
```

基础 Compose 默认将 Trace 采样率设为 0，避免未启动 Jaeger 时产生导出错误。启用观测 profile 时显式设为 1.0。Grafana 默认账号为 `admin / serviceflow`（可通过 `GRAFANA_ADMIN_USER`、`GRAFANA_ADMIN_PASSWORD` 覆盖）。Prometheus 数据源和 ServiceFlow Dashboard 会自动加载；Jaeger 接收 OTLP HTTP traces。

## 6. 推荐演示流程

按[约5分钟业务Agent脚本](docs/guides/demo-script.md)展示：已有证据与权限边界→客户确认→取消和退款沙箱→缺少done、刷新、换账号→只读核验与数据库单次副作用。准备阶段生成全新合成夹具，现场只运行选定浏览器场景并读回结果；不现场安装依赖、上传资料或跑全套测试。

LangGraph4j、Spring AI和Qwen分别提供工作流、协议适配及模型能力，MySQL提供事务基础。项目贡献在业务状态约束、授权/确认协议、幂等账本、受约束证据选择及可复现的故障验证；不把框架、模型或真实支付渠道写成自研。

## 7. 主要接口

### 身份与商品

```text
POST /api/auth/guest
POST /api/auth/login
GET  /api/me
GET  /api/products
GET  /api/products/{id}
POST /api/products/compare
POST /api/admin/products/import
```

### 聊天、订单与工单

```text
POST /api/chat/sessions
GET  /api/chat/sessions
GET  /api/chat/sessions/{sessionId}/messages
POST /api/chat/sessions/{sessionId}/messages/stream
POST /api/chat/actions/{actionId}/confirm
GET  /api/chat/actions/{actionId}/result           # 本人只读核验已提交取消，不触发执行
GET  /api/orders/{orderNo}
GET  /api/admin/refunds
POST /api/admin/refunds/{publicId}/sandbox-events  # 仅显式启用的管理员沙箱
POST /api/admin/refunds/{publicId}/retry           # 同上
GET  /api/tickets
GET  /api/admin/tickets
PUT  /api/admin/tickets/{publicId}/status
GET  /api/admin/ai-audits
```

### 知识库

```text
POST /api/admin/knowledge-documents
POST /api/admin/knowledge-documents/{documentId}/versions
GET  /api/admin/knowledge-documents
POST /api/admin/knowledge-document-versions/{versionId}/retry
```

SSE 事件包括 `meta`、`token`、`product_selection_required`、`product_comparison`、`order`、`action_required`、`ticket`、`done` 和 `error`。

## 8. 商品与知识数据

Flyway V2 内置了经华为中国官网核验的 HUAWEI Pura 80、Pura 80 Pro 和 Pura 80 Ultra 结构化数据。价格是 2026-08-29 核验的官网起售价快照，不是实时成交价；来源登记在 [data/knowledge/huawei/README.md](data/knowledge/huawei/README.md)。

新增 [retail-v1](data/retail-v1/README.md) 包含 10 款型号、23 个 SKU、11 份资料与 88 个独立章节，来源核验日期为 2026-10-23。未知报价保存为 `null`，停售为演示商家配置；不虚构成交价和库存。知识材料是注明适用范围和来源的摘要，尚待独立人工复核。通过导入工具加载到独立评测实例，不自动替换原始演示集。

结构化商品事实保存在 MySQL；功能说明、安全提示和保修政策保存在知识文档。接入企业目录时，可以从 PIM/ERP 导出后调用 `POST /api/admin/products/import`，一次最多 500 条，同 SKU 幂等更新。

最小导入格式：

```json
{
  "products": [
    {
      "sku": "PHONE-001",
      "name": "商品名称",
      "brand": "品牌",
      "model": "型号",
      "category": "phone",
      "specs": {"screen_size": "6.7 英寸", "storage": "512 GB"},
      "listPrice": 4999,
      "saleStatus": "ON_SALE"
    }
  ]
}
```

## 9. 测试与评测

后端要求 JDK 21；本机 Java 版本不满足时可以使用 Docker 中的 Maven/JDK 21。

```powershell
cd apps/serviceflow-server
mvn verify

cd ../serviceflow-web
npm ci
npm run lint
npm test
npm run build
npx playwright install chromium
npm run test:e2e

cd ../..
powershell -ExecutionPolicy Bypass -File .\scripts\evaluation\validate-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\evaluation\run-evaluation.ps1
```

当前验证入口见开头表格；[预算检查点摘要](quality/evaluation/results/provider-budget-2026-10-25.summary.json)记录最近一次后端离线门禁：117项中112通过、5项可选本地Grader跳过，格式、打包和覆盖率通过。Python采集器沿用未修改版本32/32的结果；[刷新验收](quality/evaluation/results/recovery-refresh-validation-2026-10-25.md)记录前端13项单元、13项浏览器及对应真实数据库读回。不同检查点不合并成一次全系统验收。

远程结果以[ServiceFlow CI](https://github.com/Titans23/serviceflow/actions/workflows/ci.yml)对应提交为准。CI只运行离线模型替身与隔离Demo业务检查，不调用付费云模型。真实恢复浏览器用例需设置本轮 `RECOVERY_FIXTURES`；未设置会明确跳过，普通 `npm run test:e2e` 不能自动代表全部真实恢复场景已执行。

历史[离线记录](quality/evaluation/results/v2-implementation-verification-2026-10-24.md)、[Docker验收](quality/evaluation/results/v2-docker-verification-2026-10-24.md)和旧云报告保留各自版本及分母。旧98.5%是规则审计通过率，文件名引用不是chunk召回率；不能据此宣称“事实幻觉率0%”。

在线结果依赖云模型和网络，后续运行可能出现波动；报告只陈述实际执行结果，不把单次通过率当作长期 SLA。

## 10. 当前边界

当前不实现个性化推荐、营销排序、优惠计算、实时库存、下单支付、真实支付退款通道、GraphRAG、多 Agent 和 NL2SQL。Grader 模型训练/离线验证独立放在 [training](training/README.md)，不能与主系统上线效果混为一谈。

仓库包含超时、并发舱壁、熔断、Tracing、CI、Playwright、历史 k6 和云评测资料；各版本的实现、测试与历史证据应分别陈述。公开部署和真实资金业务还需部署治理、渠道签名与对账、容量及故障恢复验证。

## 11. 简历表述参考

> 基于Java 21、Spring Boot和LangGraph4j实现电商客服业务Agent：将订单取消拆分为身份校验、客户确认、事务执行和结果核验，使用MySQL操作账本及唯一退款请求约束副作用；在提交后丢响应、缓存丢失、服务重启和浏览器刷新等受测故障中，通过只读恢复避免自动重发取消。接入公开商品资料与云模型，实施带证据编号校验的事实回答，并分别记录规则评测、语义复核和首次留出结果。

面试时明确使用了哪些框架与模型、自己实现了哪些业务约束，以及证据覆盖范围。项目使用真实公开资料和云模型，但没有生产用户流量；退款是沙箱，未接真实支付渠道。
