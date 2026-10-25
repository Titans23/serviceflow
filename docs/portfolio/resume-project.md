# ServiceFlow：简历表述与代码证据

**ServiceFlow｜面向商品咨询与售后执行的有状态客服 Agent**

Java 21 / Spring Boot / LangGraph4j / MySQL / Redis / Milvus / RabbitMQ

下面按工程问题组织，可按岗位和篇幅选3条；验证规模放在证据材料，不作为项目介绍的主语。

- **有状态编排与请求重放**：采用 MVC、虚拟线程和 POST SSE 承载模型及业务调用，以 LangGraph4j 路由客服分支；通过 MySQL 请求状态机、会话内请求唯一键及完成消息关联处理重复提交，Redis 保存短期上下文和待确认状态，区分回答重放与业务执行幂等。
- **事实约束与检索范围**：将商品目录事实与文档证据分离，按商品、文档类型和活动版本限定 Dense/BM25 双路召回，以 RRF、Reranker、Grader 和一次查询改写筛选证据；商品回答改为模型选择事实编号、后端校验并输出完整段落，保留条件限定，抑制无依据扩写。
- **有副作用操作与未知结果恢复**：将订单取消拆为服务端参数绑定、客户确认和事务执行，以客户归属、版本及状态条件更新、唯一操作账本和同事务退款请求约束并发副作用；提交后响应丢失时只读核验持久结果，避免客户端自动重发取消，退款沙箱回调按事件与尝试批次处理重复和迟到结果。
- **异步知识版本发布**：将知识版本与 Outbox 同事务写入，使用 RabbitMQ Publisher Confirm、有限退避和消费者状态条件更新处理投递与重复消息；按章节切块并生成确定性 Chunk ID，全部写入 Milvus 后事务切换活动版本，使新版本处理失败时仍可查询旧活动资料。

## 从实现追问设计

| 工程问题 | 具体实现与取舍 | 已有验证与适用边界 |
| --- | --- | --- |
| 长耗时调用与客户端重复提交如何分开处理？ | [ChatController.stream](../../apps/serviceflow-server/src/main/java/com/serviceflow/controller/ChatController.java#L75)用虚拟线程执行并返回SseEmitter；[ChatRequestStore.begin/complete](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/support/ChatRequestStore.java#L30)与[ChatMapper请求状态SQL](../../apps/serviceflow-server/src/main/resources/mapper/ChatMapper.xml#L34)将完成消息关联到请求；[CustomerWorkflow.execute](../../apps/serviceflow-server/src/main/java/com/serviceflow/agent/CustomerWorkflow.java#L133)按会话运行图 | [真实业务报告](../../quality/evaluation/results/cloud-business-recovery-2026-10-25.md)验证完成请求重放不再调用分类模型。虚拟线程降低阻塞编程成本，不能凭此宣称吞吐提升；过期PROCESSING接管也不是所有崩溃窗口的安全重放保证 |
| 改写问题会不会换商品或改变判分问题？ | [ChatServiceImpl.stream](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatServiceImpl.java#L119)统一页面/记忆商品；[MilvusRagService.search/runPass](../../apps/serviceflow-server/src/main/java/com/serviceflow/rag/MilvusRagService.java#L67)固定本次商品与活动版本，两轮Grader均判原问题；[hybrid](../../apps/serviceflow-server/src/main/java/com/serviceflow/rag/MilvusRagService.java#L234)给Dense/BM25使用同一过滤条件 | [修复前后契约](../../quality/evaluation/results/rag-context-offline-2026-10-25.md)与[真实多轮来源核对](../../quality/evaluation/results/rag-context-followup-2026-10-25.md)。自然二轮只观察到一次，未知面积事实依然不足；没有测得检索收益或长期稳定率 |
| 引用正确为什么仍可能胡说？ | [GroundedAnswerComposer.context/compose](../../apps/serviceflow-server/src/main/java/com/serviceflow/ai/GroundedAnswerComposer.java#L31)保留整段事实并校验编号；[generateAnswer](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatServiceImpl.java#L203)先收齐选择结果，再发正文 | [语义修复与首次新来源留出](../../quality/evaluation/results/cloud-final-evaluation-2026-10-25.md#8-答案语义复核与第二批来源留出)保留修复前失败与后续裁决。牺牲自由表达及首字响应；选择仍可能不相关、不完整，不能称通用Agent或“零幻觉” |
| 确认失败后怎样知道取消是否提交？ | [ChatActionServiceImpl.confirm](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatActionServiceImpl.java#L42)只用服务端动作参数；[OrderServiceImpl.cancel](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/OrderServiceImpl.java#L46)先校验所有者，再走账本/条件更新/退款事务；[唯一键冲突后的当前读](../../apps/serviceflow-server/src/main/resources/mapper/OrderMapper.xml#L23)处理旧快照；[cancellationResult](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/OrderServiceImpl.java#L104)只查已提交本人结果 | [数据库与重启恢复](../../quality/evaluation/results/recovery-validation-2026-10-25.md)、[同标签刷新/身份隔离](../../quality/evaluation/results/recovery-refresh-validation-2026-10-25.md)。404保持未知，sessionStorage不是授权；关闭标签/跨设备、并发确认与拒绝、真实支付对账不在保证范围 |
| 数据库提交、消息投递和向量可见性如何衔接？ | [KnowledgeServiceImpl.addVersion/activate](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/KnowledgeServiceImpl.java#L67)、[KnowledgeOutboxPublisher.publishDue](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/KnowledgeOutboxPublisher.java#L38)、[DocumentConsumer.consume](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/DocumentConsumer.java#L38)及[KnowledgeMapper](../../apps/serviceflow-server/src/main/resources/mapper/KnowledgeMapper.xml#L25)分别负责提交、确认投递、状态领取和活动指针 | 已有[资料导入验收](../../quality/evaluation/results/v2-docker-verification-2026-10-24.md)与[真实云数据环境记录](../../quality/evaluation/results/cloud-final-evaluation-2026-10-25.md)。Confirm后数据库提交失败仍可能重复投递；消费者在PROCESSING时崩溃的自动恢复、同文档并发上传和乱序激活未被完整保证，不宣称exactly-once |

## 数字怎么使用

最新业务/RAG小批次用于证明明确的执行与证据边界，不用于把个人项目包装成生产规模。面试被问到验证时，可展开[业务报告](../../quality/evaluation/results/cloud-business-recovery-2026-10-25.md)、[多轮RAG报告](../../quality/evaluation/results/rag-context-followup-2026-10-25.md)和相应CI；简历正文不必堆场景数、检查数和单次延迟。

旧简历里的两个指标能找到来源，但使用条件必须讲清：

| 旧表述 | 核验结果 | 可用措辞 |
| --- | --- | --- |
| “200云场景197通过 / 98.5%” | [原始报告](../../quality/evaluation/results/cloud-evaluation-200-raw.md)于2026-08-30生成，原始167/200；[审计报告](../../quality/evaluation/results/cloud-evaluation-200-audited.md)修正冲突标签后从保留摘要/事件离线重评分为197/200，没有重新请求模型。[审计脚本](../../scripts/evaluation/audit-evaluation-report.ps1#L25)执行短语/事件规则；旧200条没有开发/留出split | “建立固定回归集并保留原始运行与标签审计结果。”需要报数时同时说167/200原始、197/200离线规则审计，不能写成当前回答准确率或留出成绩 |
| “Recall@5 97.69%” | [计算代码](../../scripts/evaluation/audit-evaluation-report.ps1#L62)只看最终引用文件名的前5项，130条要求引用的题中127条命中文件名；不是Dense召回或Rerank候选chunk的排名，缺失的阶段数据无法补造 | 从简历删除该Recall表述；可解释为历史“预期引用文件命中127/130”。不声称提高了召回率 |

两份旧报告最早可追溯的产物提交为 [ea119b31](https://github.com/Titans23/serviceflow/commit/ea119b31)，当时路径是 `evaluation/`，随后在 `b363d4a0` 移至 `quality/evaluation/`。这是产物提交，不是报告记录的运行提交。报告没有冻结源码/镜像或记录实际模型配置；该提交配置默认是qwen-plus、text-embedding-v4、qwen3-rerank，但环境变量可覆盖，不能据此认定那次运行实用了哪些版本。后续v2开发/首次留出及语义复核另见[最终报告](../../quality/evaluation/results/cloud-final-evaluation-2026-10-25.md)，不得混用分母或覆盖旧失败。

## 个人贡献与展示

LangGraph4j提供图和Checkpoint，Spring AI提供协议接口，模型提供分类/证据选择能力，MySQL提供事务及约束。项目代码实现的是范围解析、业务协议、参数与身份绑定、SQL状态迁移、故障时结果核验和验证工具；Checkpoint本身不使退款安全重放。

个人贡献仍需本人确认：哪些设计由你提出、哪些代码由你编写或修改、哪些由AI辅助、哪些验证由你独立解释和执行。选用上面的简历条目前，应能顺着链接解释输入、事务边界、失败分支及证据；不能把仓库已有能力一概写成“个人独立研发”。本轮仅核验代码与已有证据，没有替你认领开发经历。

现场按[五分钟脚本](../guides/demo-script.md)展示一次确认、一次拒绝和一次未知结果恢复；RAG使用已保存的来源证据。业务优先，现场不启动付费批次，不展示令牌、原始模型记录或真实用户内容。
