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

## 普通聊天断线恢复

普通聊天已支持在当前聊天页手动重试最新一轮。前端以 SSE `done` 确认完整回复，断线时保留已收到的正文。重试使用原 `clientRequestId` 和请求快照，后端按执行状态重放、提示处理中或重新执行。刷新后可以从会话历史查看已保存的回复。

### 实施顺序

1. [x] **识别回复中断。** 前端区分连接失败、读流异常和缺少 `done`，并保留服务端错误码、可重试标记和 HTTP 状态。
2. [x] **校验请求执行权。** 访客在同一 Redis 脚本内检查完成结果并领取锁，提交和释放时验证本次执行标识。MySQL 同样按当前执行标识完成或标记失败。
3. [x] **补齐结构化重放。** 完整回复包含商品比较、订单卡片和投诉工单。投诉重试复用原工单，取消订单继续走独立确认和只读核验。
4. [x] **开放“重试本轮”。** 复用原请求体，包括商品上下文、订单选择、候选商品 ID 和比较选择。重试在原气泡内更新，处理中保留原正文。

### 验收条件

- [x] 保存成功后丢失 `done`。复用原 ID 恢复原 `messageId`、正文和卡片，模型调用仍为一次。
- [x] 原请求正在处理。重复提交返回 `REQUEST_IN_PROGRESS`，只保存一份可重放回复。
- [x] 模型调用失败后重试。同一个助手气泡收到完整回复，登录用户的问题只写入一次。
- [x] 超时接管。旧执行提交和释放锁均被拒绝。投诉重试返回同一工单编号，数据库中只有一条对应记录。

已使用本地可控模型、隔离的 MySQL 和 Redis，以及浏览器测试验证。相关入口见 [聊天 Wiki](docs/wiki/chat.md)、[后端集成测试](apps/serviceflow-server/src/test/java/com/serviceflow/ServiceFlowIntegrationIT.java)及[聊天重试浏览器测试](apps/serviceflow-web/e2e/chat-retry.spec.ts)。

- [ ] 评估断线后继续生成及自动重试的调用成本和对恢复率的影响。
