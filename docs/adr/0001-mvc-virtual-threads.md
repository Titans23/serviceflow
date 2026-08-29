# ADR-0001：MVC + Virtual Threads

状态：Accepted

ServiceFlow 使用 Spring MVC，并以 Java 21 Virtual Threads 承载阻塞式 MySQL、Redis、Milvus 和外部模型调用。

原因：项目边界是单体业务应用，外部调用链包含多个阻塞客户端；MVC 的调试和事务语义简单，Virtual Threads 可以提升等待 I/O 时的并发密度而不引入 Reactor 全链路复杂度。SSE 仍使用异步 `SseEmitter`，避免占用请求线程。

约束：外部调用必须经过超时、Resilience4j Bulkhead 和 `AiCallExecutor`；Virtual Threads 不是无限并发，收费模型压测仍禁止直接执行。
