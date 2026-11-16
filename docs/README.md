# ServiceFlow 文档

**[架构 Wiki（离线 HTML）](architecture-guide.html)** 是当前设计的阅读入口。正文源文件是下面的 Markdown，GitHub 上可直接阅读。README 负责启动入口，Wiki 解释实现，指南给出操作步骤，历史报告保留当时的证据。

## 当前架构

| 章节 | 内容 |
| --- | --- |
| [架构导读](wiki/overview.md) | 系统组成、职责边界、源码分层 |
| [聊天与会话](wiki/chat.md) | POST SSE、请求幂等、图状态与审计 |
| [订单定位与工具规划](wiki/orders.md) | 查询计划、候选选择、商品/订单上下文 |
| [取消确认与恢复](wiki/confirmation.md) | 持久动作、事务裁决、重复请求、只读核验 |
| [异步退款](wiki/refund.md) | 数据库任务、消息通知、租约与人工接手 |
| [工单与退货](wiki/after-sales.md) | 持续对话、单件全额退货、收货后退款 |
| [知识版本](wiki/knowledge.md) | 上传、Outbox、租约、活动版本切换 |
| [RAG 链路](wiki/rag.md) | 中文分词、混合检索、重排、改写与事实编号 |
| [RAG 评测](wiki/evaluation.md) | 题目构造、证据标签、指标口径与最新真实数据 |
| [运行与排查](wiki/operations.md) | Demo/Cloud、观测、权限与数据边界 |
| [验证与开发](wiki/verification.md) | 测试地图、订单性能、Wiki 维护方式 |
| [术语](wiki/glossary.md) | 各技术词在当前代码中的具体含义 |

## 操作手册与决策

- [开发环境启动](guides/quickstart.md)、[接口导航](guides/api.md)、[五分钟演示](guides/demo-script.md)。
- [隔离评测与退款运行手册](guides/evaluation-and-refund.md)、[故障演练](runbooks/fault-drills.md)。
- [架构决策 ADR](adr/)：MVC/虚拟线程、事实与 RAG 分离、Outbox/幂等。
- [Grader SFT](../training/README.md)、[代码学习指南](guides/grader-sft-learning-guide.md)：独立可选实验，不是主服务前置步骤。
- [开发检查](../CONTRIBUTING.md)、[后续工作](../TODO.md)。

## 证据与历史

- [评测索引](../quality/evaluation/README.md)：协议、数据集、回归与云质量结果。
- [最新 RAG v2 报告](../quality/evaluation/results/rag-retrieval-v2-2026-11-16.md)、[v1 五组对照](../quality/evaluation/results/rag-retrieval-ablation-2026-11-16.md)。
- [性能目录](../quality/performance/)、[历史设计/交付索引](archive/README.md)。
- [简历与面试表述的证据边界](portfolio/resume-project.md)：历史口径供追溯，最新数值以相应实验报告为准。

文档中不保存密钥、客户上传、原始模型响应或构建产物。Wiki 的 HTML 是唯一有意提交的生成文档；构建命令见 [验证章节](wiki/verification.md#文档如何维护)。
