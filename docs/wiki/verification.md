# 验证、性能与开发入口

每种检查只能证明自己覆盖的部分。单元测试、真实数据库故障验收、浏览器操作和云模型评测分别保存结果。

## 验证地图

| 检查 | 入口 | 能验证什么 |
| --- | --- | --- |
| 后端离线检查 | `mvn -DskipITs verify` | 单元测试、格式、架构约束与覆盖率 |
| MySQL 集成测试 | `mvn verify` | Flyway、行锁、重复确认、事务和退款约束 |
| 前端 | `npm run lint`、`npm test`、`npm run build` | 类型、界面状态与产物构建 |
| Python 评测契约 | `python -B -m unittest discover -s scripts/evaluation/tests` | 标签、分母、追踪解析与报告计算 |
| 真实 API / 浏览器 | [CI](../../.github/workflows/ci.yml) 与 [运行手册](../guides/evaluation-and-refund.md) | 取消、恢复、退款渠道、工单和退货 |
| 云模型评测 | [RAG 评测](evaluation.md) | 冻结问题与模型配置下的实际行为 |

可选本地 Grader 验收需要模型服务；跳过不会成为通过证据。CI 使用 Demo 与本地模拟渠道，不调用付费模型。具体开发命令见 [CONTRIBUTING](../../CONTRIBUTING.md)。

## 测试从哪里读

- [ServiceFlowIntegrationIT](../../apps/serviceflow-server/src/test/java/com/serviceflow/ServiceFlowIntegrationIT.java)：确认与放弃竞争、幂等、退款租约和数据库结果。
- [OrderConversationTest](../../apps/serviceflow-server/src/test/java/com/serviceflow/agent/OrderConversationTest.java)：查询计划、订单归属、候选选择和取消意图。
- [RagRewriteDiagnosticsTest](../../apps/serviceflow-server/src/test/java/com/serviceflow/rag/RagRewriteDiagnosticsTest.java)：检索模式和原问题约束。
- [前端 E2E](../../apps/serviceflow-web/e2e/)：区分 mock 界面场景与真实后端场景。
- [refund_delivery.py](../../scripts/evaluation/refund_delivery.py)：独立渠道响应丢失、重复处理、租约和人工接手。
- [recovery_demo.py](../../scripts/evaluation/recovery_demo.py)：事务已提交、响应缺失、缓存丢失与服务重启。

## 订单查询性能

原先将完整订单号放进模糊 keyword 条件，会扫描大量属于该客户的订单。增加 `orderNo` 精确参数后使用已有唯一索引；普通商品关键词仍保留模糊匹配语义。网页识别完整编号后选择精确参数，同一查询仍校验客户归属。

通过 EXPLAIN ANALYZE 核对扫描路径，在 **100 万合成订单、单客户 20 万订单** 的本地预热测试中，**8 并发 HTTP P95 从 1.67 s 降到 10.4 ms**。这是特定数据分布和完整订单号查询下的对照，不是所有订单查询或生产吞吐保证。

- [测量结果](../../quality/performance/reports/order-lookup-2026-11-04.json)
- [性能场景](../../quality/performance/)
- [历史优化报告](../optimization-report-2026-11-04.md)
- [OrderMapper.xml](../../apps/serviceflow-server/src/main/resources/mapper/OrderMapper.xml)

## 文档如何维护

本 Wiki 的正文源文件在 `docs/wiki/`。修改 Markdown 后执行：

```powershell
# 缺依赖时先在 apps/serviceflow-web 执行 npm ci
node scripts/docs/build-wiki.mjs
node scripts/docs/build-wiki.mjs --check
```

生成的 `docs/architecture-guide.html` 内嵌正文、搜索索引、样式和脚本，可直接离线打开。源码链接和外部报告需要打开仓库；Wiki 正文阅读与搜索不需要联网。不要直接编辑生成文件。

README 只介绍定位与入口；指南保存操作步骤；Wiki 解释当前设计；ADR 保存决策理由；历史报告与实验结果保持原时点。实现变化时修改对应章节，新的评测只添加新报告，不覆盖旧成绩。
