# ServiceFlow 评测基线

当前基线是 [serviceflow-eval-v2.json](datasets/serviceflow-eval-v2.json)，配合 [retail-v1 数据包](../../data/retail-v1/README.md) 和 Python 标准库执行器。历史 `serviceflow-eval-200.json`、Smoke 集、PowerShell 执行器及旧报告保留用于回溯，不能与 v2 分数直接比较。

## 范围与分组

| 项目 | 数量 / 口径 |
| --- | --- |
| 主评测 | 200 场景，253 步，220 次聊天调用 |
| 多轮会话 | 30 场景，在同一个 session 中连续执行 |
| 原始问题文本 | 185 个不同文本；指代问题会跨商品重复使用 |
| 开发 / 留出 | 175 / 25 场景，按来源族隔离，Mate X6 与 FreeClip 为留出来源 |
| 知识证据 | 80 场景指向来源 ID + 章节 ID；同容量族不跨集合 |
| 人工语义复核入口 | 111 场景提供参考答案，默认 `pending` |
| 退款专项 | 另有 10 场景、61 步、11 次聊天调用 |

这些是项目编写的回归场景，订单、用户与金额是合成测试夹具，不是脱敏真实客户流量，也不是独立人工标注的行业基准。来源族隔离不能消除问题模板相似性；留出结果主要反映未调试型号上的回归表现。

主集覆盖商品参数、未知价格、上下文指代、保修政策、投诉幂等、权限、取消确认/拒绝/重放/换单等。退款专项覆盖成功重放、失败重试、旧批次迟到、金额不符、事件冲突、终态冲突、越权、未付款、未来批次与事件跨退款绑定。

## 计分约定

- 每项指标只统计适用步骤的分母；需要终止事件的步骤同时检查 SSE `done/error`。
- 商品指标要求 ID 集合完全一致，不能夹带错误商品。
- `textAll` 每组至少命中一个等价短语，并且所有组都必须命中；`textNone` 检查明确禁止的短语。这是短语规则，不是事实正确率。
- 规则引用检查仍只检查文档文件名集合。新版本另发 `rag_evidence` 分块 ID/来源事件，可保存实际证据进行逐轮语义核对；历史批次没有分块追踪，不追补 chunk Recall、MRR 或 nDCG。
- `semanticAccuracy` 固定为 `null`，等待独立人工语义复核；不能把短语规则通过当作“幻觉率 0%”。
- 业务断言在操作后重新读订单/支付/退款状态；确认请求和回调可以重复使用原 ID，验证幂等。
- Demo 只能验证确定性接口和业务流程；主集知识质量必须在真实检索和模型环境中另行测量。

## 离线检查

```powershell
python -B scripts/evaluation/eval_v2.py validate
python -B scripts/evaluation/eval_v2.py validate --suite quality/evaluation/datasets/refund-sandbox-v1.json
python -B -m unittest discover -s scripts/evaluation/tests
```

校验包括 SKU、来源、章节引用、重复场景、集合隔离、断言结构。执行器测试另行验证同会话、多行 SSE、类型保留、请求重放与指标分母。它们不调用云模型，不修改数据库。

## 在线执行与证据

完整操作见[评测与退款运行手册](../../docs/guides/evaluation-and-refund.md)。必须使用显式标记的本机隔离实例 `SERVICEFLOW_EVALUATION_ENABLED=true`，先生成并导入专属合成夹具；每次业务评测使用新夹具，不能拿已取消的订单重跑初始状态。

默认仅运行 development；holdout 应在调试冻结后单独执行。云运行必须先确定公开数据范围与费用预算，再使用 `--allow-cloud --max-chat-turns N --mode cloud`。N 是聊天次数上限的授权检查，不是模型请求数或费用的硬上限，一轮可能触发分类、改写、检索、评分和生成多次请求。

报告保存在 Git 忽略的 `reports/<runId>/`。`responses.raw.json` 只留本机；`summary.json` 含适用分母、数据包各文件与评测集 SHA-256。发布到 `results/` 前人工复核和脱敏。禁止把原始回答、账号令牌或 API Key 放入公共报告。

2026-10-24 的离线状态见[本地验证记录](results/v2-implementation-verification-2026-10-24.md)，重启后的数据库与退款接口验收见[Docker 验证记录](results/v2-docker-verification-2026-10-24.md)。development 在 Demo 下为 111/175，剩余 64 项仅文档引用未通过。

2026-10-25 完成[小规模真实 Cloud 验证](results/cloud-rag-smoke-2026-10-25.md)：首轮 9 场景、10 轮对话，8/8 文档引用通过；剩余多轮报价场景经两次定向复测后通过。共 14 轮云对话，结果不代表完整开发集、留出集或独立语义评测成绩。`prepare_cloud_smoke.py` 可生成同样的公开子包和验证输入，CI 仅校验输入，不调用模型。

随后完成[完整云评测与最终报告](results/cloud-final-evaluation-2026-10-25.md)：开发集 175/175，冻结版本首次留出集 23/25，两个路由缺陷修复后已知问题回归 2/2。原始留出分数保留，不将修复后的已见用例当作独立留出成绩。

长批次可用 `--workers 3`（默认 1），失败定向验证可用 `--case-ids ID ...`；筛选必须仍属于选定 split。每个完成场景会立即写入忽略目录的 `completed.raw.jsonl`，最终结果保持数据集原有顺序。它保留中断前的证据，但不表示支持自动重放中断的业务操作。

## 语义复核与新来源留出

`semantic-regression-v1.json` 是已见问题的开发回归（12场景、15轮）；`semantic-holdout-v2.json` 是新厂商来源留出（14场景、19轮、3个多轮）。后者配套 [retail-holdout-v2](../../data/retail-holdout-v2/README.md)，不能用旧华为资料包校验。`retail-v1.1` 补全原华为知识文档遗漏的系统升级说明，原 v1 保留。

`semantic_review.py` 是有费用的 AI 辅助筛查，不是独立人工裁判。事实、引用、完整性、多轮分别评估；非法来源 ID、缺失维度与 API 失败保留为错误，不能计作通过。输出目录必须全新，原始记录只留本机。初轮裁判存在漏判，最终结论还需逐项对照证据，不把 CLEAR 计作语义正确率。

```powershell
python -B scripts/evaluation/eval_v2.py validate --suite quality/evaluation/datasets/semantic-holdout-v2.json --package data/retail-holdout-v2
# 完成已授权的云运行后，设 $run 为 reports/<runId>/responses.raw.json 的真实路径。
python -B scripts/evaluation/capture_evidence.py --run $run --docker D:/Applications/Docker/resources/bin/docker.exe
# 下面的输出目录必须未使用；max-cases 显式限定本批次请求数。
python -B scripts/evaluation/semantic_review.py --runs $run --suite quality/evaluation/datasets/semantic-holdout-v2.json --package data/retail-holdout-v2 --evidence-snapshot (Join-Path (Split-Path $run) 'evidence.raw.json') --allow-cloud-review --max-cases 14 --output quality/evaluation/reports/new-semantic-review
```

在第一轮新留出前冻结代码、镜像、资料、问题与标签；开发回归和留出结果分别报告。完整说明见[最终评测报告](results/cloud-final-evaluation-2026-10-25.md)。

## RAG阶段诊断（仅隔离评测）

原问题与改写问题分开：改写用于召回/重排，Grader始终按原问题判断。阶段证据可记录候选ID/排名、机制原因码、查询变化及耗时，正文仍单独保存在忽略目录。采集指定的RAG步骤必须使用 `--require-trace-steps CASE:INDEX ...`，缺任何指定trace时报告incomplete，不能因其他步骤有trace就宣称完整。

[离线诊断验证](results/rag-diagnostics-validation-2026-10-25.md)明确区分工程模拟和真实云结果；[3轮/27次请求计划](results/rag-cloud-validation-plan-2026-10-25.json)尚未执行，仍需预算停止保障和新的具体授权。原12/14与已见1/2不变。
