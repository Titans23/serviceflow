# v2 数据、评测与退款实现验证

记录时间：2026-10-24（Asia/Shanghai）。这是本次工作区的实现验证，不是 v2 模型质量成绩。未调用收费模型，未导入当前业务数据库，未修改历史评测结果。

后续更新：当日下午重启后的 Docker 验收已完成，见[容器、数据与真实退款接口验证](v2-docker-verification-2026-10-24.md)。以下保留此前离线阶段的记录及当时限制。

公开输入的 15 个文件 SHA-256 已保存为[本轮数据快照](v2-inputs-2026-10-24.sha256.json)，供后续实际运行核对数据版本；摘要中的统计不替代在线评测结果。

## 已落地

- retail-v1：23 SKU / 10 型号，11 个公开来源、11 份文档、88 个独立章节。保留型号范围、来源日期与参数条件，未知价格为 null；文档经 AI 辅助检查，待独立人工复核。
- serviceflow-eval-v2：200 场景、253 步、220 次聊天、30 个多轮场景，175 development / 25 holdout。185 个不同问题文本，来源族隔离但部分模板共享；属于项目回归集。
- 80 个知识场景有来源章节锚点；111 场景有参考答案且语义复核 pending。执行器检查完整短语组、禁止短语、精确商品集合、事件与业务状态，语义准确率保持 null。
- 退款专项：10 场景、61 步、11 次聊天。取消后读回退款状态，涵盖失败、重试、重放、越权、错误金额、旧/未来次数、终态冲突、跨退款事件冲突。
- 新增可空报价与退款迁移、按 Markdown 章节切块、退款事务/事件记录与客户/管理员界面；保留取消二次确认和所有者鉴权。
- 提供独立 Compose、合成夹具生成器、带版本回执的知识导入器与 CI 流程。原始回答限定在 Git 忽略的 reports 目录；在线报告包含数据包和评测文件 SHA-256。

## 实际执行结果

| 检查 | 结果 | 能证明什么 |
| --- | --- | --- |
| Maven 本地 verify（跳过 IT 和本地模型验收） | 83 tests，0 failure / 0 error | 单元、架构、权限边界；不是数据库集成通过 |
| Spotless / JDK Enforcer | 通过 | 格式与 JDK 21 要求 |
| 核心服务 JaCoCo | 行 233/255（91.37%），分支 145/190（76.32%） | Product / Order / Ticket / Refund 四个服务，非全仓覆盖率 |
| Python 评测工具 unittest | 24 通过 | 执行器、来源校验、断言、夹具与导入保护 |
| training/tests 离线 unittest | 70 通过 | 原训练工具回归；没有训练或评测新模型 |
| v2 / 退款集 validate | 200 / 10 场景结构校验通过 | 不代表在线回答通过 |
| 前端 ESLint / Vitest / 类型检查 / build | 通过，2 个单元测试 | 可编译与相关客户端行为 |
| Playwright Chromium | 6 通过 | 使用 API mock 的界面与交互回归 |
| 独立 Compose config --quiet | 通过 | Compose 配置可解析，未启动容器 |

后端命令：

```powershell
mvn -o -B spotless:apply -DskipITs '-Dtest=*Test,!LocalGraderAcceptanceTest' verify
```

本地详情在 `apps/serviceflow-server/target/final-v2-verify.log`、`target/surefire-reports`、`target/site/jacoco`；前端详情在 `apps/serviceflow-web/playwright-report`。这些构建产物未纳入公共结果文件。

## 尚待执行及限制

用户选择稍后处理 Docker，因此没有继续变更 WSL 或容器运行环境。MySQL V6/V7 迁移、退款并发/回滚的 8 个 Testcontainers 测试、实际知识导入、10 个退款 HTTP 场景均待 Docker 恢复后验证。CI 已更新但未触发远程运行，不能声称 CI 已绿。

真实 Milvus 检索和 Cloud 评测需要明确的数据/费用授权。没有计算新的语义准确率、chunk 检索指标或幻觉率；不能套用历史 v1 的审计通过率。知识包还需独立人工复核，订单与交易均为合成数据。

退款采用全额沙箱模式，未接支付平台、签名回调、对账与资金补偿。历史 PROCESSING 付款没有自动伪造退款请求；已有环境升级时需核对历史记录。前端仍有约 1.17 MB 的未拆分 JS 构建包警告，可作为后续性能优化项。

下一步按[运行手册](../../../docs/guides/evaluation-and-refund.md)完成隔离环境导入与真实业务验收，再授权小规模 Cloud 冒烟、冻结开发集、执行留出集并人工复核失败样本。只有完成这些步骤后，才适合在作品集中加入新的端到端效果数字。
