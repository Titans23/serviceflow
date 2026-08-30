# k6 性能基准

脚本只压测本地 Compose 的 Demo 模式，不对百炼 Chat、Embedding 或 Reranker 发起并发请求。

```bash
# 30 秒 CI Smoke（5 VU）
k6 run performance/k6/serviceflow.js

# 商品查询 100 VU / 5 分钟
k6 run -e PROFILE=products -e BASE_URL=http://localhost:8080/api performance/k6/serviceflow.js

# 订单只读查询（使用 CUSTOMER Token，仅查询，不产生副作用）
k6 run -e PROFILE=orders -e ORDER_NO=SF202608280002 performance/k6/serviceflow.js

# Demo SSE 聊天 30 并发 / 2 分钟
k6 run -e PROFILE=chat performance/k6/serviceflow.js
```

`BASE_URL` 可以指向 `http://localhost:5173/api` 以覆盖 Nginx 代理。使用 `--summary-export=performance/reports/<run>.raw.json` 保存原始数据，报告需同时记录 CPU、内存、吞吐量、P50/P95/P99 和 Virtual Threads 开关。

## 验收门槛

| 场景 | 目标 |
| --- | --- |
| HTTP 失败率 | < 1% |
| 商品查询 P95 | ≤ 300 ms |
| 订单查询 P95 | ≤ 500 ms |
| Demo SSE 完整响应 P95 | ≤ 3 s |

## 2026-08-30 实测结果

环境：AMD Ryzen 7 5800H（8 核 16 线程）、15.9 GB 内存、Windows 10 19045、Docker Engine 29.7.2。测试均直连本地后端，AI/RAG 使用 Demo 模式。测试结束后的容器占用为 server 664.4 MiB、MySQL 513.9 MiB、Redis 62.27 MiB；该数值是测试后快照，不冒充峰值。

| 场景 | 请求/迭代 | 吞吐 | 失败率 | P95 | P99 | 结果文件 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 商品查询，100 VU / 5 分钟 | 29,902 请求 | 99.30 req/s | 0% | 8 ms | 19 ms | `reports/products-virtual-threads-on.json` |
| 订单查询，50 VU / 3 分钟 | 8,952 请求 | 49.54 req/s | 0% | 10 ms | 21.51 ms | `reports/orders-virtual-threads-on.json` |
| Demo SSE，30 并发 / 2 分钟，Virtual Threads 开 | 3,505 迭代 | 28.87 iter/s | 0% | 30 ms | 290.96 ms | `reports/chat-virtual-threads-on-fixed.json` |
| Demo SSE，30 并发 / 2 分钟，Virtual Threads 关 | 3,507 迭代 | 28.88 iter/s | 0% | 27 ms | 294.94 ms | `reports/chat-virtual-threads-off.json` |

本机 30 并发短任务下，Virtual Threads 开关没有形成显著性能差异；选用 Virtual Threads 的主要原因仍是简化大量阻塞式 SSE/外部 I/O 的线程模型。`chat-virtual-threads-on.json` 保留了首次失败证据：因限流环境变量名错误导致 49.7% 请求失败，修正配置后的结果为 `chat-virtual-threads-on-fixed.json`。

仓库不宣称未验证的云模型吞吐量，也不使用本地 Demo 数据代替百炼并发性能。
