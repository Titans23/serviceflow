# ServiceFlow 后续工作

先复用已有结果定位问题，再决定是否需要新的实验。这里记录尚未完成的事项；已完成的设计见 [Wiki](docs/architecture-guide.html)。

## RAG：从检索命中到答案范围

已完成中文 analyzer 校验、真实分块追踪、必需/可选/禁止事实标签，以及 104 条回归 + 40 条冻结新题的四组验证。结果见 [RAG v2](quality/evaluation/results/rag-retrieval-v2-2026-11-16.md)，旧数据与成绩保留。

- [ ] 人工复核 `fresh-pura80-2`、`knowledge-nova14-wifi`、`knowledge-pura80-wet`：区分答案超范围、Grader 不足与题目/标签边界。
- [ ] 扩展新来源、更多候选和易混淆段落，避免已知商品约 8 个章节带来的召回天花板。先冻结输入，再申请新的付费范围。
- [ ] 在独立人工抽样基础上校准语义评审；规则通过率与 LLM-as-Judge 分别报告，不用引用命中替代正确性。
- [ ] 如需比较稳定差异，预先确定重复次数和统计口径，不挑选多轮最好结果。

## 商品实体与中文表达

完整订单号和明确状态追问已有确定性路径。商品口语别名、混合意图和否定表达仍需基于实际失败样例扩展。

- [ ] 补充口语型号、未知 SKU、跨商品追问和混合取消意图的冻结样例。
- [ ] 根据失败样例决定是否需要别名规范化；不先引入没有调用需求的别名服务或数据库表。
- [ ] 在真实 Cloud 结果与确定性后端验证之间保留区别，规则覆盖不当作模型准确率。

相关入口：[CustomerWorkflow](apps/serviceflow-server/src/main/java/com/serviceflow/agent/CustomerWorkflow.java)、[OrderConversation](apps/serviceflow-server/src/main/java/com/serviceflow/agent/OrderConversation.java)、[评测协议](quality/evaluation/rag-v2-protocol.md)。
