# ServiceFlow

基于 Java 21、Spring Boot、LangGraph4j 和 Vue 3 的 AI 客服与售后系统。支持商品问答、本人订单查询、取消确认、异步退款、工单协作与单件全额退货。

项目重点是把模型理解接到可核验的业务执行上：模型提出查询计划、选择证据，后端负责权限、确认、事务、幂等和未知结果恢复。

**[打开架构 Wiki](docs/architecture-guide.html)** · [Markdown 目录](docs/README.md) · [快速启动](docs/guides/quickstart.md) · [五分钟演示](docs/guides/demo-script.md)

Wiki 可下载后用浏览器离线打开，包含全文搜索、源码入口、请求链路和评测说明。GitHub 不直接渲染 HTML 时，可阅读上方 Markdown 目录。

## 主要设计

- **业务 Agent**：LangGraph4j 路由与受约束订单查询计划；候选订单选择、显式取消确认和用户归属校验。
- **取消与退款**：确认决定、订单变化和退款任务同事务提交；数据库任务表驱动 RabbitMQ 通知，租约限制旧消费者写入，渠道结果不明时先查原退款编号。
- **RAG**：商品/活动版本过滤，中文 BM25 与向量混合检索、RRF、Reranker、Grader 和一次有界改写；模型选事实编号，后端校验后组织来源段落。
- **可验证交付**：单元与 MySQL 集成测试、隔离退款故障演练、真实浏览器恢复场景和冻结数据集云评测分开报告。

## 已有测量

| 范围 | 结果 | 证据与限制 |
| --- | --- | --- |
| 中文分词配对 | BM25 Recall@5 **56.75% → 100%**，MRR@5 **0.3657 → 0.9828** | 同问题、同文档的 126 道可回答题；[v2 报告](quality/evaluation/results/rag-retrieval-v2-2026-11-16.md) |
| 冻结新题 | 四种检索配置均 **39/40** 规则通过；P95 **2.930–3.401 s** | 已知来源上的新问题，单并发；不是人工语义准确率，也未证明混合/重排增加通过数 |
| 订单号查询 | HTTP P95 **1.67 s → 10.4 ms** | 本地预热、8 并发、百万合成订单/单客户 20 万；[性能报告](quality/performance/reports/order-lookup-2026-11-04.json) |

最新 RAG 共 144 题 × 4 配置、576 次正式问答，含入库 1932 次供应商请求。历史结果及原始记录保留；数据构造、分母和失败案例见 [评测章节](docs/wiki/evaluation.md)。

## 本地运行

需要 Docker Desktop / Docker Compose。默认 Demo 用于业务链路演示，不需要模型凭据。

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up --build -d
```

Web：<http://localhost:5173>，Swagger：<http://localhost:8080/api/swagger-ui.html>。演示客户 `customer / Customer123!`，管理员 `admin / Admin123!`；只供本地使用。

Cloud 模型配置、知识导入与观测组件见 [快速启动](docs/guides/quickstart.md)。业务故障演示使用独立 Compose 和合成数据，步骤见 [演示脚本](docs/guides/demo-script.md)，不要混用主开发环境端口。

## 目录与维护

| 目录 | 职责 |
| --- | --- |
| [apps/serviceflow-server](apps/serviceflow-server/) | Java 后端、MyBatis XML、Flyway、单元与集成测试 |
| [apps/serviceflow-web](apps/serviceflow-web/) | Vue 前端与浏览器验收 |
| [apps/refund-channel](apps/refund-channel/) | 独立 HTTP + SQLite 退款模拟渠道 |
| [data](data/) | 可追溯的公开商品与知识数据 |
| [quality/evaluation](quality/evaluation/) | 冻结题目、协议、公开结果；原始响应仅本地保存 |
| [quality/performance](quality/performance/) | 查询性能场景与结果 |
| [training](training/) | 可选 Grader SFT 实验与独立 CLI |
| [docs](docs/) / [scripts](scripts/) | Wiki、运行指南、ADR、历史记录与自动化入口 |

开发检查见 [CONTRIBUTING](CONTRIBUTING.md)，剩余工作见 [TODO](TODO.md)。Wiki 正文编辑 `docs/wiki/`，执行 `node scripts/docs/build-wiki.mjs` 生成单文件阅读器。

退款渠道为模拟实现，未接入真实支付结算；固定商品 RAG 评测不是完整 Agent 能力基准。设计边界和源码详见 Wiki，历史交付结论见 [历史文档索引](docs/archive/README.md)。
