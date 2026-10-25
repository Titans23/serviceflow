# 重启后的 Docker、数据与退款验收

日期：2026-10-24，Asia/Shanghai。承接[此前离线验证](v2-implementation-verification-2026-10-24.md)，本轮已完成隔离容器环境的数据库与业务接口验收。没有调用收费模型，也没有连接真实支付平台。

## 运行环境

Docker Desktop 引擎 29.9.2 已正常运行，WSL 2 的 `docker-desktop` 正常。程序在 `D:\Applications\Docker`，数据盘位于 `D:\DockerData\wsl\disk\docker_data.vhdx`，系统盘位于 `D:\DockerData\wsl\main\ext4.vhdx`。

独立 Compose project 为 `serviceflow-eval`，数据库为 `serviceflow_eval_v2`。五个容器均已运行，server、mysql、redis、rabbitmq 的健康检查通过；通过 Web 反向代理访问 readiness 为 UP，商品目录返回 HTTP 200。访问：

- Web：<http://127.0.0.1:15173>
- 客户订单：<http://127.0.0.1:15173/orders>
- 管理员退款：<http://127.0.0.1:15173/admin/refunds>
- 服务信息：<http://127.0.0.1:18080/api/actuator/info>

AI/RAG 均为 demo，evaluation 与 refundSandbox 开关为 true。管理员沙箱不转移真实资金。保留评测容器和卷，未清空原有项目数据库。

## 本轮实测

| 检查 | 结果 | 证据与边界 |
| --- | --- | --- |
| Testcontainers | 8/8，无失败、错误或跳过 | 独立 MySQL / Redis / RabbitMQ；[摘要](refund-integration-2026-10-24.summary.json) |
| Flyway | V1–V7 成功 | 全新数据库迁移；含 V6 可空报价与 V7 退款结构，不代替已有业务数据升级验收 |
| 商品导入 | 包内 23 SKU 成功 | 总目录 24 SKU，保留 1 个原有 SKU |
| 知识入库 | 24/24 READY，活动版本均为最新版本 | 23 个 SKU 说明对象 + 1 个政策对象，独立来源章节仍为 88 |
| 重复导入 | 24/24 UNCHANGED READY | 所有文档仍为版本 1，无重复文档版本 |
| 真实退款 HTTP 场景 | 10/10，61/61 HTTP 状态断言 | 聊天、确认、查询、回调、重试均请求真实服务；[摘要](refund-sandbox-2026-10-24.summary.json) |
| 主集 development | 111/175 场景通过 | 剩余 64 个场景仅文档引用检查失败；[摘要](development-demo-2026-10-24.summary.json) |
| 浏览器测试 | 6/6，通过容器 Web 执行 | 使用 API mock 的界面测试，不能与真实 HTTP 退款场景混称 |
| 新增 Demo 意图回归 | 2/2 测试方法通过 | 覆盖 8 个输入：否定查询、对比转折、未收到订单/退款等 |
| Python 评测工具 | 24/24 | 修复夹具数据库名保护后重跑 |
| Spotless | 通过 | 103 个 Java 文件；其他未变更部分复用此前本地通过结果 |

主集运行共 225 步，HTTP 状态 225/225、预期 SSE 事件 202/202、意图 191/191、商品 ID 144/144、短语覆盖 42/42、禁止短语 20/20，全部订单/付款/幂等状态断言通过。文档引用为 0/64：Demo RAG 返回内置示例，不检索刚导入的真实文档；如实保留失败和进程退出码 1，没有放宽引用断言。

退款专项验证：成功重放、失败重试、旧次数迟到、事件载荷冲突、金额不符、客户越权、未付款取消、终态冲突、未来次数、事件跨退款绑定和重复确认。数据库测试进一步验证并发重复回调只写一个事件，以及支付状态写入失败时退款和事件一起回滚。

## 实测发现与修复

1. 容器原先静默预下载全部 Maven 插件依赖，排查困难；传递 POM 还引入 snapshot/milestone 仓库。现在保留构建日志，使用 BuildKit 的 Maven 缓存，并通过 `build-settings.xml` 限定当前发布版依赖从 Maven Central 获取，直接打包。首次完整依赖准备后，后续改动已成功复用缓存重建。
2. 合成夹具保护把 `serviceflow_eval_` 长度写成 16，导致正确的隔离数据库也被拒绝。保护在任何业务写入前触发；已改为 SQL `CHAR_LENGTH` 计算长度，用新夹具成功导入，未绕过或削弱数据库边界。
3. Demo 分类器将“先不用查商品”“暂时没有具体的商品或订单问题”误判为业务请求。已忽略明确拒绝的查询片段，保留转折后的有效请求及“没有收到订单/退款”问题，并补充回归测试。初次 development 为 109/175，修复后用新订单夹具重跑为 111/175；没有改动评测标签。

本轮新增 2 个本地测试方法；此前 83 个本地测试及核心服务覆盖率通过结果对未变更代码继续有效，不把这些分批检查描述成一次全量 `mvn verify`。

## 尚未证明的能力

- 本轮没有真实 Milvus 向量索引、Cloud 生成或独立人工语义复核；`semanticAccuracy` 仍为 null，没有新的云模型质量分数或“零幻觉”结论。
- 25 个 holdout 场景保持未运行，留待开发冻结后的真实模型评测。
- 实际支付退款、渠道签名、对账、部分退款与真实交易流量仍不在当前实现范围。
- 本机验收已执行；远程 GitHub CI 未触发，不能称远程 CI 已通过。

## 复现与本地证据

运行步骤见[评测与退款手册](../../../docs/guides/evaluation-and-refund.md)。本机原始回答和运行回执仅保存在 Git 忽略的 `quality/evaluation/reports/`，公共结果只包含统计及输入文件摘要。

- 退款运行：`20261024-133943-3c845404`。
- 修复后的 development：`20261024-134404-5903fd59`。
- 数据库测试：`apps/serviceflow-server/target/integration-20261024.log`。
- Demo 回归：`apps/serviceflow-server/target/demo-routing-20261024.log`。
- 镜像构建：`quality/evaluation/reports/docker-build-central-20261024.log` 及 `docker-build-routing-20261024.log`。

下一阶段需要在经授权的隔离 Cloud 环境完成真实文档向量入库、小规模模型冒烟、开发集与留出集评测，并独立复核语义答案。不能把当前 Demo 的规则通过率替代这一步。
