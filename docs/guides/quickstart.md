# 开发环境启动

## 前置条件

- Docker Desktop，包含 Docker Compose。
- 业务执行与恢复演示使用隔离Demo模式，不需要API Key；真实云问答才需要阿里云百炼配置与数据/费用授权。

求职展示直接使用[五分钟脚本的准备步骤](demo-script.md)，地址为15173/18080。以下步骤是主Compose的通用开发启动，地址为5173/8080，默认Demo回答不能证明真实RAG质量。

## 配置环境变量

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
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

## 启动

```powershell
docker compose up --build -d
docker compose ps
```

首次启动会创建持久卷并执行 Flyway 迁移；Milvus Collection 会在首次知识入库或检索时惰性初始化。知识文档需要在管理员页面上传；仓库中的 `data/knowledge/huawei` 提供了可演示文档。

## 访问地址

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

## 启动可观测性组件

```powershell
$env:TRACING_SAMPLING_PROBABILITY = '1.0'
docker compose --profile observability up -d
```

基础 Compose 默认将 Trace 采样率设为 0，避免未启动 Jaeger 时产生导出错误。启用观测 profile 时显式设为 1.0。Grafana 默认账号为 `admin / serviceflow`（可通过 `GRAFANA_ADMIN_USER`、`GRAFANA_ADMIN_PASSWORD` 覆盖）。Prometheus 数据源和 ServiceFlow Dashboard 会自动加载；Jaeger 接收 OTLP HTTP traces。
