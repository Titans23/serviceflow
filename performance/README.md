# k6 性能基准

脚本只压测本地 Compose 的 Demo 模式，不对百炼 Chat、Embedding 或 Reranker 发起并发请求。

```bash
# 30 秒 CI Smoke（5 VU）
k6 run performance/k6/serviceflow.js

# 商品查询 100 VU / 5 分钟
k6 run -e PROFILE=products -e BASE_URL=http://localhost:8080/api performance/k6/serviceflow.js

# 订单只读查询 需要一个客户订单号（仅查询，不产生副作用）
k6 run -e PROFILE=orders -e ORDER_NO=SF-1001 performance/k6/serviceflow.js

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

当前仓库不伪造云模型吞吐量；只有实际运行后才把结果写入 `performance/reports/`。
