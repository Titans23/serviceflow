# 固定RAG批次：出站上限与外部故障停止验收

针对已核准的[4场景/7轮/最多63次方案](rag-context-followup-plan-2026-10-25.md)，扩展现有EvaluationProviderBudget的隔离评测开关，不改变普通运行的降级策略。尚未进行本批次真实模型调用。

## 保障与范围

共享计数仍在发送前准入，失败扣次。新overlay启用stop-on-provider-failure：HTTP错误、连接失败、同步body读取IO、SSE读取失败/缺少DONE、应用deadline超时会锁存本预算终止状态，后续三种provider客户端不能继续出站。已发出的请求仍计入预算，不能撤回供应商已经处理的任务或据此减免费用。

流式路径保留原始body的单次消费，只对同一流做转换，用最多32字符尾部识别OpenAI-compatible SSE终止标记，不缓存完整回答。有效DONE可跨网络chunk；未收到标记的EOF按不完整处理。该规则针对本项目兼容协议，不声称覆盖所有供应商的断流形式。无法核对记录或API响应不完整时，批次停止并保留未知。

固定串行执行入口 `run_rag_followup.py` 复用已有run_case，每个API前后核对同一容器/StartedAt/RestartCount及连续预算日志。语义/规则失败可以继续；外部故障、计数或身份无法核对则不开始剩余槽位。实际失败响应在抛出停止信号前落盘，未发送的槽位显式标记notAttempted。对HEAD检查已暂存和未暂存变化，并要求suite/plan/collector已跟踪且纳入指纹；.publish不动。不会用通用eval_v2命令绕开该入口。

## 本地真实客户端证据

- 27和63请求两种上限均通过：成功、500、断连、重定向尝试计数；第limit+1次及另两种客户端后续尝试在发送前拒绝，重定向目标0请求。
- stop开关开启后，有效SSE将DONE拆成两个网络chunk发送，正常完成分类，接着Embedding/Reranker仍成功；计数3，无错误停止。
- 429、连接在响应头前断开、声明body长度后提前断开、SSE缺少DONE四种真实loopback HTTP故障，各只收到1次请求，随后Chat/Embedding/Reranker均0次新wire。
- 应用deadline夹具仅用本地延迟supplier，0次wire；它证明deadline触发锁存，不冒充真实网络超时测试。
- 普通模式无预算对象且重试设置仍为2；默认预算模式未启用新停止开关时保留此前故障注入和降级行为。

新增正向测试曾发现新钩子使用body替换导致“response body can only be consumed once”。该次预算测试4项中1失败，日志保留；已改为body transform，正向与负向4项重新通过。它是本地评测钩子的开发失败，不是云失败或旧屏幕拒答根因。

后端完整门禁执行130项：125通过、5个可选本地Grader跳过；随后正向钩子修复只重跑受影响的4项预算测试，未重复其他未变化用例。格式通过。Python工具41/41，覆盖provider终止后不再发API、进程改变不伪造完整计数、失败响应落盘后停止。suite验证为4case/7turn/2multi，Compose仅做静默配置校验；最终执行还须对应提交CI成功。

本地日志分别保留上限27/54的中间检查、最终27/63检查、正向初次失败、修复后正负验收，不把中间结果改写为一次全通过。没有新增SKU、语料、模型服务或观测平台。
