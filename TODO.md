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

普通聊天通过 SSE 接收正文。后端保存完整回复后发送 `done`，前端目前在响应流结束时停止加载，缺少 `done` 时也可能留下半截回答。后端已能按原 `clientRequestId` 重放完成结果，普通聊天页面每次发送仍会生成新 ID。

### 实施顺序

1. [ ] **识别回复中断。** 前端以 SSE `done` 确认本轮收全。连接失败、读流异常或读到流末尾仍缺 `done` 时，保留已收到的正文，并标记“回复未完整接收”。保留 SSE `error` 的 `code`、`retryable` 和 HTTP 状态供界面判断。
2. [ ] **校验请求执行权。** 访客抢到 Redis 锁后再查一次完成结果，覆盖上一次请求恰好在查缓存和抢锁之间完成的情况。使用每次执行独有的标识，原子校验并保存结果或释放锁。登录请求在接管时更新执行标识，`complete` 和 `fail` 只接受当前执行。旧执行不得覆盖新结果或删除新锁。
3. [ ] **补齐结构化重放。** 随正文保存并恢复商品比较表等用户可见结果，保留现有订单、候选和待确认卡的恢复路径。投诉工单沿用原请求标识，验证重试返回同一工单。取消订单继续走独立确认和只读结果核验。
4. [ ] **开放“重试本轮”。** 复用原会话、`clientRequestId` 和完整请求体，包括商品上下文、订单选择及候选商品 ID。新流收到正文时替换原气泡的半截内容。已完成时重放，已失败时重新执行，收到 `REQUEST_IN_PROGRESS` 时保留原回复并提示稍后重试。遇到限流、鉴权失败或无效参数时显示具体原因并结束本次重试。后台继续生成和自动重试留待评估调用成本及恢复效果后决定。

### 验收条件

- [ ] 保存成功后丢失 `done`：重试复用原 ID，恢复原 `messageId`、正文和卡片，模型调用仍为一次。
- [ ] 原请求生成中且仍持有执行权：重复提交返回 `REQUEST_IN_PROGRESS`，只产生一份用户可见结果。
- [ ] 模型调用失败后重试：同一个助手气泡收到完整回复，登录用户的该轮用户消息只保存一条。
- [ ] 旧执行超时被接管后恢复：旧执行无法覆盖新结果或清掉新锁。投诉场景核对工单编号和记录数量。

先用本地可控的假模型和隔离数据验证。相关入口见 [聊天页面](apps/serviceflow-web/src/views/ChatView.vue)、[聊天服务](apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatServiceImpl.java)、[请求状态更新](apps/serviceflow-server/src/main/resources/mapper/ChatMapper.xml)及[丢失 `done` 的端到端测试](apps/serviceflow-web/e2e/recovery-live.spec.ts)。
