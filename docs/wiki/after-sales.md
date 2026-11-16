# 工单对话与退货

顾客和客服通过持久工单继续协作，退货验收复用同一退款执行链路。

## 顾客与客服的持续工单对话

`GET /tickets` 与详情接口按登录客户限制归属，管理员从独立接口接手与回复。`ticket_message` 保存 CUSTOMER/SUPPORT 消息，内部 `resolution_note` 不出现在顾客详情中。列表和详情分别限制最近 200 张工单、最近 200 条消息。

### 回复去重与状态转换

发送回复先锁工单，再检查状态和消息 UUID。重复 UUID 仅在工单、角色和内容一致时返回原结果，不能用同一编号改变内容。顾客在 RESOLVED 后补充消息，会在同一事务将状态恢复为 ASSIGNED；顾客确认解决只允许 RESOLVED→CLOSED，重复关闭返回原结果。已关闭工单拒绝追加回复。

前端在响应不明时保留原回复 UUID，重试沿用它；路由变化时不把旧工单请求的结果更新到新工单。V14 将历史上已明确发布的 `customer_message` 迁入会话，内部备注不迁为公开消息。

## 收货后的单件全额退货

客户在订单详情提交原因，`POST /orders/{orderNo}/return` 从登录身份检查订单归属。首版每单一张申请，仅接受已签收、已付款、无退款且只有一条商品明细、数量为 1 的订单。金额取订单实付总额，商品名称与金额在申请中留存。重复申请返回原单，包括已拒绝或完成的申请。

### 验收与退款事务

客服在退货处理页填写顾客可见的退回指引或拒绝原因。`REQUESTED → APPROVED / REJECTED` 审核决定持久保存，重复相同决定返回原结果，冲突决定拒绝。退回商品由顾客按指引自行寄送或交回，客服确认实物验收并填写结果后，`APPROVED → RECEIVED`、付款退款状态、唯一全额退款请求和后台投递任务在同一事务提交。金额依据发生变化或任一写入失败会回滚收货与退款记录。

### 并发与原编号复用

退货写入先锁订单，再锁退货单，并采用 READ COMMITTED，使等待并发请求提交后读取到其最新结果。退货编号复用为退款编号，重复收货直接返回原结果。收货后的退款沿用查询优先、回调幂等、失败重试和人工核验流程，退款最终状态从既有退款记录读取。订单详情和订单咨询返回同一 `returnRequest`，包括审核指引、验收记录及退款进度。客服审核和实物验收由管理员执行。

## 源码阅读入口

- [TicketServiceImpl](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/TicketServiceImpl.java)
- [ReturnService](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/ReturnService.java)
