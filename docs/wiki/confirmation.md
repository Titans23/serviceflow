# 取消确认与结果恢复

将用户确认、订单状态变化与退款任务放在同一事务中裁决。

## 确认执行与未知结果

`OrderConversation`生成绑定客户、会话、订单版本和来源请求的 `chat_action`，有效期 10 分钟。同一请求重复准备返回同一动作。`ActionDecisionService.decide`通过 `SELECT ... FOR UPDATE` 串行裁决同一动作的确认与放弃，取得会话共享锁以与逻辑删除排序，再复核订单版本和可取消状态。动作最终结果、订单条件更新、支付退款状态、唯一退款请求和操作账本在同一事务提交。SSE 返回在事务提交后开始，传输失败不回滚已经生效的业务，也不自动重做。

## 重复决定与最终状态

结果接口可以读取 PENDING、CONFIRMED、REJECTED、EXPIRED、INVALIDATED、CONFLICT；PENDING 仍表示尚未取得最终结果，不能据此自动重发。确认胜出后再放弃、放弃胜出后再确认，都返回第一次裁决。历史的迁移前取消账本仍可查询；迁移前仅存在 Redis 中的未确认卡片需要重新发起申请。

## 浏览器如何核验未知结果

确认或放弃响应缺失时，浏览器通过`GET /chat/actions/{id}/result`查本人持久决定与订单；404、PENDING 或网络异常保留待核验标识，不自动重发决定。历史确认卡恢复时先查询动作状态，只有有效 PENDING 卡能继续显示。退款沙箱按eventId/attemptNo/金额及终态约束回调与重试。sessionStorage只是身份分区的关联线索，Checkpoint不是退款执行凭证。

## 源码阅读入口

- [ActionDecisionService](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/ActionDecisionService.java)
- [ChatActionServiceImpl](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/ChatActionServiceImpl.java)
- [OrderServiceImpl](../../apps/serviceflow-server/src/main/java/com/serviceflow/service/impl/OrderServiceImpl.java)
