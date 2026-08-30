# 部署与运维配置

`docker-compose.yml` 保留在仓库根目录，作为本地开发和面试演示的统一入口。本目录保存 Compose 引用的部署资产：

- `observability/prometheus/`：Prometheus 抓取配置；
- `observability/grafana/`：Grafana 数据源、Dashboard 与自动加载配置。

部署目录不得保存密钥或环境专属 `.env`；敏感配置通过环境变量注入。
