# ADR-0003：事务 Outbox 与请求幂等

状态：Accepted

知识版本和 Outbox 在同一事务写入，Publisher Confirm 成功后才标记已发布。客户聊天请求使用 `(session_id, client_request_id)` 唯一键和 `PROCESSING/COMPLETED/FAILED` 状态机。

原因：将数据库已提交而尚未发送的事件持久保留，使Broker暂时失败后可继续投递。Confirm成功而发布事务未提交仍可能重投；消费者的状态条件更新、READY短路和确定性分块处理普通重复，不能消除所有崩溃窗口或保证跨MySQL/Milvus exactly-once。PROCESSING崩溃自动接管、版本单调激活仍未实现。

聊天请求重放与副作用幂等分开：完成聊天可重放旧消息而不再调用模型；FAILED/过期PROCESSING有条件接管，但不能据此宣称安全重放任意工具。订单取消先校验归属，使用唯一操作账本、条件UPDATE和同事务唯一退款请求；重复键当前读解决旧快照可见性。结果不明时只读查询本人已提交账本，404不授权重发，业务写操作不由模型重试层重试。详见[取消与恢复代码证据](../portfolio/resume-project.md#从实现追问设计)。
