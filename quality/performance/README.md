# k6 性能基准

脚本只压测本地 Compose 的 Demo 模式，不对百炼 Chat、Embedding 或 Reranker 发起并发请求。

```bash
# 30 秒 CI Smoke（5 VU）
k6 run quality/performance/k6/serviceflow.js

# 商品查询 100 VU / 5 分钟
k6 run -e PROFILE=products -e BASE_URL=http://localhost:8080/api quality/performance/k6/serviceflow.js

# 订单只读查询（使用 CUSTOMER Token，仅查询，不产生副作用）
k6 run -e PROFILE=orders -e ORDER_NO=SF202608280002 quality/performance/k6/serviceflow.js

# Demo SSE 聊天 30 并发 / 2 分钟
k6 run -e PROFILE=chat quality/performance/k6/serviceflow.js
```

`BASE_URL` 可以指向 `http://localhost:5173/api` 以覆盖 Nginx 代理。使用 `--summary-export=quality/performance/reports/<run>.raw.json` 保存原始数据，报告需同时记录 CPU、内存、吞吐量、P50/P95/P99 和 Virtual Threads 开关。

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

## 2026-11-04 订单查询与超时资源验证

本轮使用独立的 `serviceflow-refund-verification` 环境，在 `serviceflow_eval_refund_verification` 追加 100 万笔合成订单，其中一个客户有 20 万笔。已有 Cloud 实例的数据不参与压测。测量同一个完整编号的查找：基线网页发送 `keyword`，优化网页发送新增的精确参数 `orderNo`，返回同一笔本人订单。旧 `keyword` 接口保留模糊语义。数据与容器内存限制保持一致；商品关键词和列表查询不属于这次索引优化的效果范围。

```powershell
# 首次生成数据并测量基线；必须先运行需要测量的基线镜像。
python -B scripts/performance/order_lookup.py --phase baseline --prepare --rows 1000000 --label sustained --duration-seconds 30 --source-revision ef8ba849 --allow-isolated-load
# 更换为优化镜像，复用上一步打印的 receipt.json；不要重新生成数据。
python -B scripts/performance/order_lookup.py --phase optimized --receipt quality/evaluation/reports/order-performance-<runId>/receipt.json --label sustained --duration-seconds 30 --allow-isolated-load
```

程序限制项目、数据库和本地地址，并要求明确启用负载测试。每档并发 1/8/16、预热 5 次，再运行 30 秒闭环负载并等待已发出请求结束；报告记录实际请求数、错误、P50/P95/P99、吞吐、镜像 ID、源码版本/Mapper 摘要及等价参数展开查询的 EXPLAIN ANALYZE。Windows 上用高精度 `perf_counter`，避免毫秒级测量受系统时钟粒度影响。结果代表本机固定业务查询，不能作为生产容量上限。

`AiCallExecutorTimeoutTest` 另以两轮、每轮 20 个可响应中断的本地替身验证超时后资源释放。优化前等待超时但任务仍运行；优化后显式取消 Future。对照数据见[超时资源记录](reports/timeout-cancellation-2026-11-04.json)。此项不调用收费模型，也不证明第三方服务停止执行或停止计费。

最终持续测量见[订单查询对照](reports/order-lookup-2026-11-04.json)，业务背景、完整表格、局限和本轮其他改动见[优化报告](../../docs/optimization-report-2026-11-04.md)。16 并发下完整编号查找 P95 为 3440.21 → 14.67 ms；普通列表对照没有显示同样改善，不作全站性能提升声明。
