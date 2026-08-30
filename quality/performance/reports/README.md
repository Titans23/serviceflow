# 性能报告目录

运行 k6 后，将脱敏的 Markdown 报告和原始 JSON 放在此目录。报告至少包含运行日期、Commit、CPU/内存、Compose 配置、Virtual Threads 开关、吞吐量、P50/P95/P99、错误率和是否达到门槛。

## 2026-08-30 CI Smoke 实测

- 环境：Windows + Docker Desktop，AMD Ryzen 7 5800H，主机内存 15.9 GB，Docker 可用内存 7.689 GiB。
- 服务：Docker Compose Demo 模式，Java 21 Virtual Threads 开启；MySQL 8.4、Redis 7.4、RabbitMQ 4、Milvus 2.6.22。
- 负载：5 VU，30 秒；每次迭代签发 GUEST Token 并查询一次商品列表。
- 请求：300；迭代：150；HTTP 吞吐量 9.87 req/s。
- 检查：300/300 通过；HTTP 失败率 0.00%。
- 商品查询延迟：P50 7 ms、P95 9 ms、P99 10 ms、最大 11 ms。
- 全部 HTTP 延迟：P50 5.9 ms、P95 8.82 ms、P99 9.46 ms、最大 10.4 ms。
- 资源快照：server 608.3 MiB、MySQL 452 MiB、RabbitMQ 128.8 MiB、Redis 4.988 MiB；快照不是峰值。
- 结论：满足 Smoke 的失败率 `<1%` 与商品查询 P95 `≤300 ms` 门槛。

本结果只证明本机 Demo Smoke，不代表云模型吞吐或正式容量。100 VU 商品基准、订单基准、SSE 基准及 Virtual Threads 开关对照仍应单独执行。原始 `ci-smoke.raw.json` 只保留在本机并由 `.gitignore` 排除。
