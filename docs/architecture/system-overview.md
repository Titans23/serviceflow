# ServiceFlow 架构与请求时序

## 运行拓扑

```text
Vue 3 + Nginx
      │ REST / POST SSE
Spring Boot 3.5 (MVC + Virtual Threads)
  ├─ Auth / Product / Order / Ticket / Chat 应用服务
  ├─ CustomerWorkflow (LangGraph4j 确定性路由)
  ├─ Spring AI StreamingChatModel + EmbeddingModel接口
  ├─ Milvus Hybrid RAG + 百炼 Reranker 适配器
  ├─ MySQL 8.4 (事实、订单、正式会话、确认决定、幂等、Outbox)
  ├─ Redis 7 (限流、近期记忆、当前商品/订单、候选订单、Checkpoint)
  ├─ RabbitMQ 4 (知识入库事件 + DLQ、退款任务通知)
  └─ 独立本地退款渠道 (Python HTTP + SQLite，仅模拟资金结果)

Prometheus ── Grafana（observability profile）
Jaeger <── OTLP Trace（observability profile）
```

## 一次聊天请求

1. `ChatController` 校验请求并获取当前主体，立即创建 `SseEmitter`。
2. CUSTOMER 请求由 `ChatRequestStore` 在 MySQL 原子插入 `PROCESSING`；完成请求直接重放助手消息，遗留请求超过 2 分钟才允许接管。GUEST 使用 Redis 30 分钟幂等记录。
3. `ChatController.stream`将实际工作交给虚拟线程；`ChatServiceImpl`调用`CustomerWorkflow`。Router结合模型分类与业务规则选择CHAT、PRODUCT_QUERY、KNOWLEDGE_QUERY、ORDER_QUERY或COMPLAINT。RedisSaver按sessionId记录图状态，但不负责业务副作用的可靠重放。
4. 产品请求先从 MySQL 读取结构化事实，再按 `productId` 和活动知识版本过滤 Milvus；比较事件仅使用类别白名单字段。
   商品页提示仅绑定入口会话，首次商品响应后不再逐轮覆盖记忆；手动选择历史会话会清除入口提示。页面与记忆商品都经过本轮明确SKU/型号的切换判断，未知SKU清除旧引用，避免旧商品范围进入检索与事实池。
5. RAG执行Dense/BM25各Top20、RRF融合、Reranker Top5和Grader；不足时至多改写一次并重检。两轮固定商品/文档类型/活动版本，Grader均判原问题。Reranker失败保留RRF Top5，Grader失败保留候选但判不足，累计降级不会被后轮成功清除；无证据不使用模型常识补全。

商品事实回答采用受约束的证据选择：云模型返回本轮事实编号，后端校验编号后输出目录事实或完整检索段落，阻止自由扩写参数和使用条件。选择完成后再发送正文 token，因而不再逐 token 转发模型自由生成。事实选择是否相关、是否完整仍需要评测；原始资料错误也不会被这种机制自动纠正。`rag_evidence` SSE 元数据仅提供分块 ID 和来源，不包含原文。
6. `SseEventWriter` 统一发出 `meta`、`token`、结构化业务事件、`done` 或稳定错误码，并在关闭/超时/异常时递减连接指标。
7. 客户请求的begin事务登记请求与用户消息，complete事务登记助手消息与完成状态；两者之间包含外部模型调用，不是一笔长事务。正式客户审计另行写入，当前实际包含完整问题/回答，不能宣称已自动脱敏；公共报告只保留脱敏结果，不提交原始记录、JWT或API Key。

## 我的订单与多订单追问

`GET /orders` 从登录身份取得 customerId，按商品名称/型号/SKU/类别、订单号、状态和下单日期查询本人订单。新增 `orderNo` 精确筛选参数，使用现有唯一索引；原 `keyword` 继续保持文字匹配语义。网页识别到 `SF + 至少12位数字` 的完整编号时使用 `orderNo`，其他输入使用 `keyword`。日期按北京时间解释，结束日包含全天；分页用创建时间与 ID 的组合游标，游标先校验归属。同页商品明细一次批量读取，避免每笔订单再发一条 SQL。

从订单卡进入客服会创建独立会话，页面传入 `pageContext.orderNo`，后端使用前再次查归属。Cloud 模式在 ORDER_QUERY 分支调用模型输出一个受约束的 JSON 查询计划：`SEARCH_ORDERS`、`READ_CURRENT`、`SELECT_CANDIDATE` 或 `ASK_USER`。后端校验工具名、参数形状、日期范围及候选序号，再调用既有 OrderService；这是一轮规划加确定性执行，没有自动循环调用工具。

查询唯一命中时展示订单；多笔命中时返回 `order_selection_required`，最多展示 10 笔，并在 Redis 按主体与会话缓存候选、queryId 和待处理意图 30 分钟。用户可回复“第二笔”或点击卡片。卡片必须匹配当前 queryId 和候选集合，选中后再次读取本人订单。重新查询清除旧订单指代；旧卡片失效后要求重新查询。候选卡和订单事实随助手消息存入 MySQL，支持历史展示及相同 clientRequestId 的结果重放；历史卡片展示不延长候选的可选择期限。

“取消昨天买的耳机”先查本人订单，多笔时先选定，再生成绑定该订单的确认卡。查询、选择候选、生成确认卡都不会修改订单或创建退款。订单事实由后端查询结果生成；模型识别日期、条件和指代的效果仍需独立的 Cloud 评测，确定性测试不代表模型准确率。

## 确认执行与未知结果

`OrderConversation`生成绑定客户、会话、订单版本和来源请求的 `chat_action`，有效期 10 分钟。同一请求重复准备返回同一动作。`ActionDecisionService.decide`通过 `SELECT ... FOR UPDATE` 串行裁决同一动作的确认与放弃，取得会话共享锁以与逻辑删除排序，再复核订单版本和可取消状态。动作最终结果、订单条件更新、支付退款状态、唯一退款请求和操作账本在同一事务提交。SSE 返回在事务提交后开始，传输失败不回滚已经生效的业务，也不自动重做。

结果接口可以读取 PENDING、CONFIRMED、REJECTED、EXPIRED、INVALIDATED、CONFLICT；PENDING 仍表示尚未取得最终结果，不能据此自动重发。确认胜出后再放弃、放弃胜出后再确认，都返回第一次裁决。历史的迁移前取消账本仍可查询；迁移前仅存在 Redis 中的未确认卡片需要重新发起申请。

确认或放弃响应缺失时，浏览器通过`GET /chat/actions/{id}/result`查本人持久决定与订单；404、PENDING 或网络异常保留待核验标识，不自动重发决定。历史确认卡恢复时先查询动作状态，只有有效 PENDING 卡能继续显示。退款沙箱按eventId/attemptNo/金额及终态约束回调与重试。sessionStorage只是身份分区的关联线索，Checkpoint不是退款执行凭证。

## 后台退款与人工接手

取消事务在 `refund_request` 之外同时插入 `refund_delivery`，后者也是持久投递记录；历史退款不自动补投。发布器沿用 Publisher Confirm 与 `SKIP LOCKED` 的方式通知 RabbitMQ，任务完成前可再次投递，同一消息不代表一次新的退款。两个消费者以数据库条件更新取得 30 秒租约，新的 leaseToken 阻止恢复后的旧工作线程覆盖新任务。领取和结果提交使用短事务，HTTP 在事务外执行。

消费者每次先查询稳定的退款 publicId。只有渠道明确返回未受理，或管理员已将明确失败的旧批次推进为下一批次时，才允许提交。同一业务退款编号、金额和批次由独立渠道检查；超时与异常保持 WAITING，每 5 秒再核验，发布器每 2 秒扫描。10 分钟仍无法得到结果时，事务内创建幂等人工工单并标记 MANUAL，支付仍为 PROCESSING。管理员可以恢复核验；这个动作沿用原编号并先查询，不能把“未知”直接改为“失败重退”。

渠道回调需独立凭证，仍复用原有金额、eventId、attemptNo、终态校验；过期租约结果和旧批次回调不能覆盖新状态。已成功退款、重复消息、重复回调不会重复改款。当前渠道是独立持久化的本地模拟服务，支持响应丢失、回调丢失、明确失败、长时间处理中和进程重启；这些结果不能宣称已经对接真实支付机构。

复现入口为 `scripts/evaluation/refund_delivery.py --allow-isolated-faults`，使用专用 Compose 项目、数据库及端口。CI 同时运行真实消息队列和浏览器人工接手测试，报告只发布合成数据的检查摘要。

模型调用等待超时或上游线程被中断时，`AiCallExecutor` 显式取消 Future 并保留调用线程的中断标记。受测可响应中断任务不会在返回超时后继续堆积；这不代表外部服务已经停止执行或停止计费，退款等已提交业务仍依靠稳定编号和结果核验恢复。

## 知识入库时序

```text
上传 → MySQL document/version=PENDING + Outbox=PENDING（同一事务）
     → Publisher Confirm → RabbitMQ
     → Consumer 幂等解析/Tika/Chunk/Embedding/Milvus
     → READY → 原子切换 active_version_id
```

新版本普通处理失败不会切换旧活动指针。发布器在数据库事务内`SKIP LOCKED`领取到期事件并等待Confirm，失败有限退避；Confirm后数据库提交失败仍可能重复投递。消费者通过PENDING→PROCESSING领取、READY短路和确定性Chunk ID处理普通重投，不能称跨存储exactly-once。

当前没有PROCESSING崩溃自动接管，也没有阻止旧版本晚完成后回切活动指针的单调条件；同文档并发上传版本分配/文件路径也未完整保证。文件、MySQL和Milvus并非一个事务。已有资料导入和活动版本过滤证据支持正常发布路径，不能扩写为所有崩溃窗口可恢复。具体代码与取舍见[工程证据表](../portfolio/resume-project.md#从实现追问设计)。
