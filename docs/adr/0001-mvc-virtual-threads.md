# ADR-0001：MVC + Virtual Threads

状态：Accepted

ServiceFlow 使用 Spring MVC，并以 Java 21 Virtual Threads 承载阻塞式 MySQL、Redis、Milvus 和外部模型调用。

原因：项目边界是单体业务应用，外部调用链包含多个阻塞客户端；MVC 的调试和事务语义简单，Virtual Threads 可以提升等待 I/O 时的并发密度而不引入 Reactor 全链路复杂度。SSE 仍使用异步 `SseEmitter`，避免占用请求线程。

当前边界：分类/Grader/改写及Embedding/Reranker通过`AiCallExecutor`施加deadline，流式回答在`CloudAiGateway.streamAnswer`直接使用90秒等待边界；客户端另有超时与Bulkhead配置。不能把所有外部调用都说成经过同一执行器。虚拟线程不增加下游配额或数据库连接数，历史Demo对照也未证明显著吞吐收益。

SSE连接结束不等于业务未提交。完成请求重放由MySQL请求记录负责，取消结果恢复由操作账本负责，与线程/Checkpoint机制分开；详见[请求与事务时序](../architecture/system-overview.md)。收费模型不用于默认CI或现场压测。
