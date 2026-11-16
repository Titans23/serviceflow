# 运行、观测与排查

先决定运行的是业务 Demo 还是付费 Cloud 问答，再选择对应的配置与端口。两者的验证目的不同。

## 启动入口

| 用途 | 入口 | 地址与注意事项 |
| --- | --- | --- |
| 通用本地开发 | [快速启动](../guides/quickstart.md) | Web 5173 / API 8080 |
| 业务恢复演示 | [五分钟脚本](../guides/demo-script.md) | 独立 Compose，Web 15173 / API 18080 |
| 多轮业务与退款验收 | [隔离运行手册](../guides/evaluation-and-refund.md) | 使用合成账号、订单和专用数据库 |
| 真实 RAG 对照 | [评测协议](../../quality/evaluation/rag-v2-protocol.md) | 独立预算、冻结输入、出错停止 |
| 本地 Grader 实验 | [training/README](../../training/README.md) | 独立 CLI 与模型服务，不是主应用启动前置 |

默认 Demo 不需要 API Key；其确定性回答用于检查业务链路，不能作为 RAG 模型效果。Cloud 的 Chat/Embedding 使用 OpenAI-compatible 协议，Reranker 使用百炼原生协议；变量名里的 OPENAI 不代表同时依赖两家供应商。

## 身份与接口

JWT 区分 GUEST、CUSTOMER 和 ADMIN。客户查询从登录身份取得 customerId，不接受模型或浏览器指定其他用户；管理知识、退款处置和评测接口要求管理员。访客的会话、限流和请求去重主要保存在 Redis；正式会话和操作结果保存在 MySQL。

[接口导航](../guides/api.md) 便于定位功能，完整字段以 Swagger 和对应 Controller 为准。取消订单必须经过待确认动作，读查询接口不能顺便执行取消或退款。

## 观测先看哪一层

- HTTP 状态与 SSE 事件：先确认请求结束、超时，还是业务已提交但响应中断。
- 请求编号、动作编号和退款 publicId：串联浏览器、数据库与渠道，避免用新编号重试未知结果。
- [RagTraceRecorder](../../apps/serviceflow-server/src/main/java/com/serviceflow/rag/RagTraceRecorder.java)：区分无召回、重排失败、Grader 不足和答案选择问题。
- Outbox、版本状态和租约：区分“消息已发”与“数据已可用”。
- Prometheus / Grafana / Jaeger：通过 Compose 的 observability profile 开启。首正文指标是服务端起点到首段正文，不是浏览器 TTFT。

## 常见现象与处理

| 现象 | 先核查 | 处理原则 |
| --- | --- | --- |
| 确认后页面没有结果 | 持久动作结果、订单状态 | 按原动作编号只读查询，不自动重发决定 |
| 退款一直处理中 | 渠道原编号、任务租约、deadline、人工工单 | 查询优先，不把超时当作失败后再退一次 |
| 新文档上传后仍回答旧内容 | 新版本是否 READY、active_version_id | 完整入库后才激活，旧版本暂时继续服务 |
| RAG 全部检索失败 | Collection、analyzer、向量维度、文档版本 | 明确修复配置；旧集合不自动删除 |
| Recall 高但回答不合格 | Grader 后覆盖、所选编号、标签范围 | 先定位错误阶段，不盲目增加检索步骤 |

## 数据与日志边界

`.env`、上传原文件、原始模型回答、JWT 和 API Key 不提交。原始评测在被忽略的 `quality/evaluation/reports/`，公开可核查的汇总在 `results/`。独立 AI 审计默认不复制问题与答案正文，但正式聊天历史仍存在，不能声称全系统匿名化。

故障注入只使用 [专用演练](../runbooks/fault-drills.md) 指定的隔离环境。清理构建临时文件不等于清理数据卷、历史报告、训练数据或模型权重。
