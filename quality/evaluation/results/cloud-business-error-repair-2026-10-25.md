# 业务拒绝与动作失效：SSE错误语义修复

为真实模型取消→退款验收做预检时，在现有Demo服务发起一次随机不存在动作的确认请求，实际收到HTTP200/SSE `INTERNAL_ERROR`、`retryable=true`。该请求没有有效动作、没有业务副作用，也未调用云模型。不存在/过期动作不应被描述为可重试的内部故障。

原 `ChatController.run` 只识别最外层ServiceFlowException，ResponseStatusException以及图/执行器包装后的业务错误都落入可重试内部错误。新增7项测试，修复前6失败、1通过，覆盖403/404/409/400包装、直接过期动作、显式模型超时及未知错误脱敏。

最小修复只调整SSE错误转换：沿异常原因链保留已定义ServiceFlowException的错误码和retryable；403/404/409/400分别转为FORBIDDEN/NOT_FOUND/CONFLICT/BAD_REQUEST且不可重试；其他未知错误保留通用提示。HTTP已建立SSE时仍通过error事件返回，不改变订单授权、确认、事务或退款逻辑。

修复后完整后端 `mvn -DskipITs verify` 通过，124项中119通过、5项可选本地Grader跳过，0失败/错误；格式、打包、覆盖率门禁通过。前端未改变，复用既有浏览器验收。38项Python工具测试及Compose合并配置通过，当前检查点尚未运行新付费模型批次。

新镜像在隔离Demo实际启动后，用另一个随机不存在动作重复HTTP检查：仍为HTTP200/SSE，但错误已为 `NOT_FOUND`、`retryable=false`。前后均没有有效动作、订单修改或云请求。

复现：`mvn -Dtest=ChatControllerErrorTest test`。原始本地失败日志和HTTP前后证据保留在忽略的reports目录；没有通过弱化权限断言将内部错误算作业务拒绝。
