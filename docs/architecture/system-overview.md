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
6. `SseEventWriter` 统一发出 `progress`、`meta`、`token`、结构化业务事件、`done` 或稳定错误码，并在关闭/超时/异常时递减连接指标。工作流执行前发送处理进度；首段非空正文产生时记录 `serviceflow.chat.first_token_server`，口径是服务端处理起点到正文产生，不包含缓存重放，也不代表浏览器收到数据的时间。
7. 客户请求的 begin 事务登记请求与用户消息，complete 事务登记助手消息与完成状态；两者之间包含外部模型调用，不是一笔长事务。独立 AI 审计默认不复制问题/回答正文，读取接口默认隐藏历史正文；`SERVICEFLOW_AUDIT_RECORD_CONTENT=true` 可显式开启。原聊天历史与旧审计数据均保留，不能宣称已删除或全面匿名化。公共报告只保留脱敏结果，不提交原始记录、JWT 或 API Key。

## 我的订单与多订单追问

`GET /orders` 从登录身份取得 customerId，按商品名称/型号/SKU/类别、订单号、状态和下单日期查询本人订单。新增 `orderNo` 精确筛选参数，使用现有唯一索引；原 `keyword` 继续保持文字匹配语义。网页识别到 `SF + 至少12位数字` 的完整编号时使用 `orderNo`，其他输入使用 `keyword`。日期按北京时间解释，结束日包含全天；分页用创建时间与 ID 的组合游标，游标先校验归属。同页商品明细一次批量读取，避免每笔订单再发一条 SQL。

从订单卡进入客服会创建独立会话，页面传入 `pageContext.orderNo`，后端使用前再次查归属。Cloud 模式在 ORDER_QUERY 分支调用模型输出一个受约束的 JSON 查询计划：`SEARCH_ORDERS`、`READ_CURRENT`、`SELECT_CANDIDATE` 或 `ASK_USER`。后端校验工具名、参数形状、日期范围及候选序号，再调用既有 OrderService；这是一轮规划加确定性执行，没有自动循环调用工具。

完整订单号查询，以及已有明确订单且没有待选候选时的限定状态追问，直接进入订单分支并查询业务服务，省去分类与规划模型调用。规则采用完整匹配；投诉、取消和混合请求不会被这个快捷路径吞掉。快捷路径同样使用登录客户的身份查归属，不能跳过授权，也不以规则命中率代表模型理解能力。

查询唯一命中时展示订单；多笔命中时返回 `order_selection_required`，最多展示 10 笔，并在 Redis 按主体与会话缓存候选、queryId 和待处理意图 30 分钟。用户可回复“第二笔”或点击卡片。卡片必须匹配当前 queryId 和候选集合，选中后再次读取本人订单。重新查询清除旧订单指代；旧卡片失效后要求重新查询。候选卡和订单事实随助手消息存入 MySQL，支持历史展示及相同 clientRequestId 的结果重放；历史卡片展示不延长候选的可选择期限。

“取消昨天买的耳机”先查本人订单，多笔时先选定，再生成绑定该订单的确认卡。查询、选择候选、生成确认卡都不会修改订单或创建退款。订单事实由后端查询结果生成；模型识别日期、条件和指代的效果仍需独立的 Cloud 评测，确定性测试不代表模型准确率。

订单号直达和模型查询计划共用取消意图门槛。否定取消、询问取消状态/条件以及只查物流退款时返回查询结果；候选订单的待取消意图同时清除。明确请求取消或继续选择此前待取消候选时才允许生成确认卡。刷新仅恢复最新消息上的有效待确认卡，用户继续查询后不恢复更早的卡片。此规则采用保守的中文表达判断，复杂混合请求可能需要用户另发明确的取消指令。

## 确认执行与未知结果

`OrderConversation`生成绑定客户、会话、订单版本和来源请求的 `chat_action`，有效期 10 分钟。同一请求重复准备返回同一动作。`ActionDecisionService.decide`通过 `SELECT ... FOR UPDATE` 串行裁决同一动作的确认与放弃，取得会话共享锁以与逻辑删除排序，再复核订单版本和可取消状态。动作最终结果、订单条件更新、支付退款状态、唯一退款请求和操作账本在同一事务提交。SSE 返回在事务提交后开始，传输失败不回滚已经生效的业务，也不自动重做。

结果接口可以读取 PENDING、CONFIRMED、REJECTED、EXPIRED、INVALIDATED、CONFLICT；PENDING 仍表示尚未取得最终结果，不能据此自动重发。确认胜出后再放弃、放弃胜出后再确认，都返回第一次裁决。历史的迁移前取消账本仍可查询；迁移前仅存在 Redis 中的未确认卡片需要重新发起申请。

确认或放弃响应缺失时，浏览器通过`GET /chat/actions/{id}/result`查本人持久决定与订单；404、PENDING 或网络异常保留待核验标识，不自动重发决定。历史确认卡恢复时先查询动作状态，只有有效 PENDING 卡能继续显示。退款沙箱按eventId/attemptNo/金额及终态约束回调与重试。sessionStorage只是身份分区的关联线索，Checkpoint不是退款执行凭证。

## 后台退款与人工接手

取消事务在 `refund_request` 之外同时插入 `refund_delivery`，后者也是持久投递记录；历史退款不自动补投。发布器沿用 Publisher Confirm 与 `SKIP LOCKED` 的方式通知 RabbitMQ，任务完成前可再次投递，同一消息不代表一次新的退款。两个消费者以数据库条件更新取得 30 秒租约，新的 leaseToken 阻止恢复后的旧工作线程覆盖新任务。领取和结果提交使用短事务，HTTP 在事务外执行。

消费者每次先查询稳定的退款 publicId。只有渠道明确返回未受理，或管理员已将明确失败的旧批次推进为下一批次时，才允许提交。同一业务退款编号、金额和批次由独立渠道检查；超时与异常保持 WAITING，每 5 秒再核验，发布器每 2 秒扫描。10 分钟仍无法得到结果时，事务内创建幂等人工工单并标记 MANUAL，支付仍为 PROCESSING。管理员可以恢复核验；这个动作沿用原编号并先查询，不能把“未知”直接改为“失败重退”。

渠道回调需独立凭证，仍复用原有金额、eventId、attemptNo、终态校验；过期租约结果和旧批次回调不能覆盖新状态。已成功退款、重复消息、重复回调不会重复改款。当前渠道是独立持久化的本地模拟服务，支持响应丢失、回调丢失、明确失败、长时间处理中和进程重启；这些结果不能宣称已经对接真实支付机构。

退款转人工后，订单详情的 `refundReview` 返回原工单编号、核验进度、顾客回复和更新时间。查询通过原退款编号派生的稳定 `source_action_id` 定位工单，并再次限制订单及工单所属客户，不依赖原聊天或任务上的可空工单指针。重新核验、失败重试保留工单关联，最终进度依据退款渠道状态显示。新会话查询本人订单也会返回同一工单。客服通过运营中心“回复顾客”显式发布消息，V12 新增 `customer_message`，既有内部备注不会自动公开。

复现入口为 `scripts/evaluation/refund_delivery.py --allow-isolated-faults`，使用专用 Compose 项目、数据库及端口。CI 同时运行真实消息队列和浏览器人工接手测试，报告只发布合成数据的检查摘要。

模型调用等待超时或上游线程被中断时，`AiCallExecutor` 显式取消 Future 并保留调用线程的中断标记。受测可响应中断任务不会在返回超时后继续堆积；这不代表外部服务已经停止执行或停止计费，退款等已提交业务仍依靠稳定编号和结果核验恢复。

## 顾客与客服的持续工单对话

`GET /tickets` 与详情接口按登录客户限制归属，管理员从独立接口接手与回复。`ticket_message` 保存 CUSTOMER/SUPPORT 消息，内部 `resolution_note` 不出现在顾客详情中。列表和详情分别限制最近 200 张工单、最近 200 条消息。

发送回复先锁工单，再检查状态和消息 UUID。重复 UUID 仅在工单、角色和内容一致时返回原结果，不能用同一编号改变内容。顾客在 RESOLVED 后补充消息，会在同一事务将状态恢复为 ASSIGNED；顾客确认解决只允许 RESOLVED→CLOSED，重复关闭返回原结果。已关闭工单拒绝追加回复。

前端在响应不明时保留原回复 UUID，重试沿用它；路由变化时不把旧工单请求的结果更新到新工单。V14 将历史上已明确发布的 `customer_message` 迁入会话，内部备注不迁为公开消息。

## 收货后的单件全额退货

客户在订单详情提交原因，`POST /orders/{orderNo}/return` 从登录身份检查订单归属。首版每单一张申请，仅接受已签收、已付款、无退款且只有一条商品明细、数量为 1 的订单。金额取订单实付总额，商品名称与金额在申请中留存。重复申请返回原单，包括已拒绝或完成的申请。

客服在退货处理页填写顾客可见的退回指引或拒绝原因。`REQUESTED → APPROVED / REJECTED` 审核决定持久保存，重复相同决定返回原结果，冲突决定拒绝。退回商品由顾客按指引自行寄送或交回，客服确认实物验收并填写结果后，`APPROVED → RECEIVED`、付款退款状态、唯一全额退款请求和后台投递任务在同一事务提交。金额依据发生变化或任一写入失败会回滚收货与退款记录。

退货写入先锁订单，再锁退货单，并采用 READ COMMITTED，使等待并发请求提交后读取到其最新结果。退货编号复用为退款编号，重复收货直接返回原结果。收货后的退款沿用查询优先、回调幂等、失败重试和人工核验流程，退款最终状态从既有退款记录读取。订单详情和订单咨询返回同一 `returnRequest`，包括审核指引、验收记录及退款进度。客服审核和实物验收由管理员执行。

## 知识入库时序

```text
上传 → MySQL document/version=PENDING + Outbox=PENDING（同一事务）
     → Publisher Confirm → RabbitMQ
     → Consumer 幂等解析/Tika/Chunk/Embedding/Milvus
     → READY → 原子切换 active_version_id
```

上传先锁文档行再分配版本号，每个源文件使用独立路径，事务回滚清理本次新文件。消费者通过 PENDING→PROCESSING 领取任务，使用三分钟租约并在分块前续期。30 秒一次的恢复扫描处理超时任务，最多自动尝试三次。重试复制源文件引用，创建新版本编号和 Outbox 记录，旧任务的向量写入因此与新尝试隔离。已发布但一直未领取的 PENDING 通知会重新投递；连续五次发布失败则标记 FAILED，支持人工重建。

只有持有有效租约的 PROCESSING 版本能变为 READY。激活时按版本号单调推进，旧版本晚完成不会覆盖已生效的新版本；旧内容已经有更新上传时，也不会自动重建旧内容。新版本完整入库前，原活动版本继续服务。MySQL、文件和 Milvus 仍是独立存储，历史失败版本及其向量保留用于追溯，不宣称跨存储 exactly-once。
