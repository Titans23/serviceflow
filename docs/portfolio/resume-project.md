# ServiceFlow：Agent实习简历与面试证据

**ServiceFlow｜客服业务Agent：确认执行与未知结果恢复（Java / Spring Boot）**

以下3条是仓库已有验证证据支持的简历候选描述，可按版面选2–3条；仅使用自己确实参与且能沿代码解释的部分。仓库实现与测试通过本身不能证明个人独立完成；对AI辅助生成、修改和定位的部分如实说明，区分自己的设计判断、代码理解和实际验证工作。

- 实现订单取消的“请求→待确认→授权执行→结果核验”链路，将身份、会话和目标订单绑定到服务端动作；以订单条件更新、唯一操作账本及同事务等额退款请求约束并发和重复执行。
- 实现确认响应不完整时的只读恢复：同标签保存身份分区的动作标识，刷新/返回时查询本人已提交结果，404保持未知；恢复过程不自动重发取消，退出或换账号清理关联线索。
- 构建真实模型接入下的业务故障验收：固定6个合成场景、14轮新聊天，79项业务检查通过，保留13次预期拒绝、14次模型分类请求计数及每轮完整响应耗时；验证参数篡改、权限、重复请求和提交后丢响应恢复。

## 描述与证据对应

| 描述 | 代码阅读入口 | 已有运行证据 |
| --- | --- | --- |
| 授权、确认与幂等执行 | [待确认动作绑定](../../apps/serviceflow-server/src/main/java/com/serviceflow/agent/CustomerWorkflow.java#L462)→[确认时检查本人并执行](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatActionServiceImpl.java#L42)→[取消事务](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/OrderServiceImpl.java#L46)与[SQL约束](../../apps/serviceflow-server/src/main/resources/mapper/OrderMapper.xml#L17) | [真实模型业务报告](../../quality/evaluation/results/cloud-business-recovery-2026-10-25.md)：订单A/B切换、夹带参数、并发确认及重复退款回调；[逐项摘要](../../quality/evaluation/results/cloud-business-recovery-2026-10-25.summary.json) |
| 未知结果只读恢复 | [本人结果查询](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatActionServiceImpl.java#L34)→[持久账本关联订单](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/OrderServiceImpl.java#L104)；前端[缺少done后的处理](../../apps/serviceflow-web/src/views/ChatView.vue#L159)和[只读核验](../../apps/serviceflow-web/src/views/ChatView.vue#L169)，[身份分区标识](../../apps/serviceflow-web/src/stores/pendingCancellation.ts#L60) | [同标签刷新/身份隔离](../../quality/evaluation/results/recovery-refresh-validation-2026-10-25.md)、[Demo响应丢失与服务重启](../../quality/evaluation/results/recovery-validation-2026-10-25.md)；本次云批次的lost-response单列在业务报告 |
| 可复现验收与实际缺陷修复 | [固定场景](../../quality/evaluation/datasets/cloud-business-recovery-v1.json)、[运行脚本](../../scripts/evaluation/cloud_business_recovery.py)、[SSE错误映射](../../apps/serviceflow-server/src/main/java/com/serviceflow/controller/ChatController.java#L137) | [错误映射修复前后](../../quality/evaluation/results/cloud-business-error-repair-2026-10-25.md)、[CI异步等待缺口修复](../../quality/evaluation/results/cloud-business-ci-repair-2026-10-25.md)、[最新验收提交的成功CI](https://github.com/Titans23/serviceflow/actions/runs/40174356460) |
| 可选补充：多轮商品与证据范围 | [当前商品归一化](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatServiceImpl.java)、[完整段落摘录](../../apps/serviceflow-server/src/main/java/com/serviceflow/ai/GroundedAnswerComposer.java)、[固定云验证器](../../scripts/evaluation/run_rag_followup.py) | [离线缺陷修复](../../quality/evaluation/results/rag-context-offline-2026-10-25.md)、[最新4场景/7轮开发诊断](../../quality/evaluation/results/rag-context-followup-2026-10-25.md)：31/63次尝试，切换/代词、未知SKU边界与实际来源一致；自然二轮1次仍保留不足，没有新独立留出成绩 |

## 面试追问与回答要点

**模型和项目代码分别决定什么？**

云模型通过[CloudAiGateway](../../apps/serviceflow-server/src/main/java/com/serviceflow/ai/CloudAiGateway.java#L49)分类，工作流还会进行[规则修正](../../apps/serviceflow-server/src/main/java/com/serviceflow/agent/CustomerWorkflow.java#L209)。订单参数从当前消息或会话状态提取，确认接口使用服务端动作，退款金额取数据库订单总额。本次没有单独保存原始分类输出，SSE intent是修正后结果，不能发布模型原始分类准确率或称为模型自主生成工具参数。

商品回答中，模型选择当前事实编号，后端只拼接验证过的完整事实段落；范围解析、参数绑定和数值出处由代码约束。[最新RAG报告](../../quality/evaluation/results/rag-context-followup-2026-10-25.md)逐项核对来源，但不把摘录约束包装为通用Agent能力。自然二轮的屏幕段落首轮已在第1；二次重检没有得到缺失面积数字，不能声称带来召回提升或解决未知事实。

**重复聊天、重复确认和重复退款回调如何处理？**

[聊天请求处理](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatServiceImpl.java#L78)在已完成的clientRequestId上重放结果，避免再调用模型；取消用actionId关联唯一操作账本，并核对客户与订单；退款请求与取消同事务创建，回调按事件和尝试状态处理。证据支持受测重复/并发路径中的单次副作用，不扩展成跨系统、真实支付的exactly-once保证。

**为什么没收到确认响应时不直接重试？404意味着什么？**

事务可能已提交而客户端没收到done。恢复GET只查本人操作账本和订单当前状态，不确认或执行；404只说明当前没有可访问的已提交结果，可能尚未完成，继续保持未知。sessionStorage是关联线索，不是授权来源；关闭标签、清空存储或跨设备恢复不在当前保证范围内。

**79/79和14轮具体说明什么？服务重启也属于这个云成绩吗？**

79是固定脚本中的业务断言数。6场景中只有happy-replay、order-switch、lost-response是同会话连续多轮；14是整批新聊天回合数，不是14段多轮对话。当前云批次使用14次真实分类请求，未重启server；服务重启证据来自另一次无云调用的Demo验收。合成99元订单、沙箱回调和数据库状态都是真实服务操作，资金转移、银行到账和对账未接入。token与金额未知保留null。

**实际定位过什么问题，如何避免把失败藏掉？**

过期动作及包装后的业务拒绝曾返回可重试INTERNAL_ERROR；新增7项回归在修复前6项失败，修复后保留稳定业务码和重试属性，并实际复验HTTP。之后CI曾因404浏览器场景尚未等完3次响应就断言而失败，修复为逐步等待真实404，仍要求确认1次、数据库零副作用。原CI失败、本地复验与最终成功CI都保留在对应报告，未为CI追加付费模型重跑。

## 框架与个人贡献边界

LangGraph4j提供状态图、Checkpoint和Saver；Spring AI提供协议适配，Qwen提供模型能力，MySQL提供事务与唯一约束。可讲的项目实现是业务状态及授权规则、确认协议、参数绑定、操作账本、退款状态处理、前端未知结果恢复和故障验收。Checkpoint不等于可安全重放的业务提交；项目未自研这些框架、模型或支付渠道。按自己确实理解和参与的部分表述贡献，阅读顺序可沿第一张表逐段追踪。

现场展示按[五分钟脚本](../guides/demo-script.md)准备，引用现有云报告；不在面试时临时启动付费实验。历史RAG规则分数、首次留出、模型筛查和来源核对保持各自口径，不与本页79项业务检查合并成“模型准确率”。
