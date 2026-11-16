# 订单定位与工具规划

自然语言先确定查询目标，再由后端执行受约束的业务操作。

## 我的订单与多订单追问

`GET /orders` 从登录身份取得 customerId，按商品名称/型号/SKU/类别、订单号、状态和下单日期查询本人订单。新增 `orderNo` 精确筛选参数，使用现有唯一索引；原 `keyword` 继续保持文字匹配语义。网页识别到 `SF + 至少12位数字` 的完整编号时使用 `orderNo`，其他输入使用 `keyword`。日期按北京时间解释，结束日包含全天；分页用创建时间与 ID 的组合游标，游标先校验归属。同页商品明细一次批量读取，避免每笔订单再发一条 SQL。

## 一次规划与确定性执行

从订单卡进入客服会创建独立会话，页面传入 `pageContext.orderNo`，后端使用前再次查归属。Cloud 模式在 ORDER_QUERY 分支调用模型输出一个受约束的 JSON 查询计划：`SEARCH_ORDERS`、`READ_CURRENT`、`SELECT_CANDIDATE` 或 `ASK_USER`。后端校验工具名、参数形状、日期范围及候选序号，再调用既有 OrderService；这是一轮规划加确定性执行，没有自动循环调用工具。

完整订单号查询，以及已有明确订单且没有待选候选时的限定状态追问，直接进入订单分支并查询业务服务，省去分类与规划模型调用。规则采用完整匹配；投诉、取消和混合请求不会被这个快捷路径吞掉。快捷路径同样使用登录客户的身份查归属，不能跳过授权，也不以规则命中率代表模型理解能力。

## 候选选择与上下文失效

查询唯一命中时展示订单；多笔命中时返回 `order_selection_required`，最多展示 10 笔，并在 Redis 按主体与会话缓存候选、queryId 和待处理意图 30 分钟。用户可回复“第二笔”或点击卡片。卡片必须匹配当前 queryId 和候选集合，选中后再次读取本人订单。重新查询清除旧订单指代；旧卡片失效后要求重新查询。候选卡和订单事实随助手消息存入 MySQL，支持历史展示及相同 clientRequestId 的结果重放；历史卡片展示不延长候选的可选择期限。

## 从查询到取消申请

“取消昨天买的耳机”先查本人订单，多笔时先选定，再生成绑定该订单的确认卡。查询、选择候选、生成确认卡都不会修改订单或创建退款。订单事实由后端查询结果生成；模型识别日期、条件和指代的效果仍需独立的 Cloud 评测，确定性测试不代表模型准确率。

订单号直达和模型查询计划共用取消意图门槛。否定取消、询问取消状态/条件以及只查物流退款时返回查询结果；候选订单的待取消意图同时清除。明确请求取消或继续选择此前待取消候选时才允许生成确认卡。刷新仅恢复最新消息上的有效待确认卡，用户继续查询后不恢复更早的卡片。此规则采用保守的中文表达判断，复杂混合请求可能需要用户另发明确的取消指令。

## 源码阅读入口

- [OrderConversation](../../apps/serviceflow-server/src/main/java/com/serviceflow/agent/OrderConversation.java)
- [CloudAiGateway](../../apps/serviceflow-server/src/main/java/com/serviceflow/ai/CloudAiGateway.java)
- [OrderCancellationIntent](../../apps/serviceflow-server/src/main/java/com/serviceflow/ai/OrderCancellationIntent.java)
- [OrderServiceImpl](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/OrderServiceImpl.java)
