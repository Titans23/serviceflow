# ADR-0003：事务 Outbox 与请求幂等

状态：Accepted

知识版本和 Outbox 在同一事务写入，Publisher Confirm 成功后才标记已发布。客户聊天请求使用 `(session_id, client_request_id)` 唯一键和 `PROCESSING/COMPLETED/FAILED` 状态机。

原因：消除“数据库提交成功但 RabbitMQ 发送失败”的窗口，并让 SSE 重试、浏览器重复提交和服务重启具备可解释行为。订单取消、工单创建仍由各自业务幂等记录保护，不由模型重试层重试。
