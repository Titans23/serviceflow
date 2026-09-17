# ServiceFlow 文档索引

| 文档 | 用途 |
| --- | --- |
| [技术报告](technical-report.md) | 技术选型、全部核心实现、测试与边界 |
| [系统架构](architecture/system-overview.md) | 组件关系、请求链路与部署拓扑 |
| [架构决策记录](adr/) | MVC + Virtual Threads、事实/RAG 分离、Outbox 与幂等 |
| [故障演练](runbooks/fault-drills.md) | RabbitMQ、Reranker、订单幂等和请求恢复证据 |
| [演示脚本](guides/demo-script.md) | 5–8 分钟项目演示流程 |
| [A6000 后训练交接](guides/a6000-post-training-handoff.md) | 模型任务边界、数据隔离、基线与 SFT 实施计划 |
| [Grader SFT 最终模型](../training/README.md) | 最终模型、统一命令入口、评测与恢复 |
| [简历项目描述](portfolio/resume-project.md) | 可追溯的简历和面试表述 |
| [原始需求与强化计划](requirements/original-plan.md) | 项目范围和历史设计输入 |

README 负责快速启动；本目录负责设计、运行和项目证据，不放置构建产物或环境密钥。
