# 异步退款与人工接手

数据库保存任务与进度，消息队列通知执行，渠道结果不明时查询原退款编号。

## 后台退款与人工接手

取消事务在 `refund_request` 之外同时插入 `refund_delivery`，后者也是持久投递记录；历史退款不自动补投。发布器沿用 Publisher Confirm 与 `SKIP LOCKED` 的方式通知 RabbitMQ，任务完成前可再次投递，同一消息不代表一次新的退款。两个消费者以数据库条件更新取得 30 秒租约，新的 leaseToken 阻止恢复后的旧工作线程覆盖新任务。领取和结果提交使用短事务，HTTP 在事务外执行。

## 查询优先与人工接手

消费者每次先查询稳定的退款 publicId。只有渠道明确返回未受理，或管理员已将明确失败的旧批次推进为下一批次时，才允许提交。同一业务退款编号、金额和批次由独立渠道检查；超时与异常保持 WAITING，每 5 秒再核验，发布器每 2 秒扫描。10 分钟仍无法得到结果时，事务内创建幂等人工工单并标记 MANUAL，支付仍为 PROCESSING。管理员可以恢复核验；这个动作沿用原编号并先查询，不能把“未知”直接改为“失败重退”。

## 回调、旧批次与渠道边界

渠道回调需独立凭证，仍复用原有金额、eventId、attemptNo、终态校验；过期租约结果和旧批次回调不能覆盖新状态。已成功退款、重复消息、重复回调不会重复改款。当前渠道是独立持久化的本地模拟服务，支持响应丢失、回调丢失、明确失败、长时间处理中和进程重启；这些结果不能宣称已经对接真实支付机构。

## 客服如何接续处理

退款转人工后，订单详情的 `refundReview` 返回原工单编号、核验进度、顾客回复和更新时间。查询通过原退款编号派生的稳定 `source_action_id` 定位工单，并再次限制订单及工单所属客户，不依赖原聊天或任务上的可空工单指针。重新核验、失败重试保留工单关联，最终进度依据退款渠道状态显示。新会话查询本人订单也会返回同一工单。客服通过运营中心“回复顾客”显式发布消息，V12 新增 `customer_message`，既有内部备注不会自动公开。

复现入口为 `scripts/evaluation/refund_delivery.py --allow-isolated-faults`，使用专用 Compose 项目、数据库及端口。CI 同时运行真实消息队列和浏览器人工接手测试，报告只发布合成数据的检查摘要。

## 模型调用的取消边界

模型调用等待超时或上游线程被中断时，`AiCallExecutor` 显式取消 Future 并保留调用线程的中断标记。受测可响应中断任务不会在返回超时后继续堆积；这不代表外部服务已经停止执行或停止计费，退款等已提交业务仍依靠稳定编号和结果核验恢复。

## 源码阅读入口

- [RefundDeliveryService](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/RefundDeliveryService.java)
- [RefundDispatcher](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/RefundDispatcher.java)
- [RefundChannelClient](../../apps/serviceflow-server/src/main/java/com/serviceflow/infrastructure/messaging/RefundChannelClient.java)
- [RefundServiceImpl](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/RefundServiceImpl.java)
