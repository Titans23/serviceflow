# ServiceFlow 评测基线

## RAG v2 修订与新问题留出

中文分词、允许补充事实的标签，以及新增 40 条未用于调参的问题见 [v2 冻结协议](rag-v2-protocol.md)。v1 数据与首轮成绩保留；v2 回归集和新问题分别报告，标签变更不能被算作模型质量提升。

v2 已完成 576 次正式问答及同文本 BM25 分词配对。40 条新题四组均为 39/40 通过；同一 v2 题集上的 BM25 Recall@5 从 standard 的 56.75% 到 chinese 的 100%。详细范围、延迟、失败样本与旧成绩区别见 [v2 结果报告](results/rag-retrieval-v2-2026-11-16.md)。

## 五组真实 RAG 对照

2026-11-16 已完成首轮 104 × 5 配置评测，525 次请求（含 5 次预热）均保留完整证据，含入库使用 1895/2400 次供应商请求。结果、数据构建口径及失败样本分析见[真实对照报告](results/rag-retrieval-ablation-2026-11-16.md)和[机器可读结果](results/rag-retrieval-ablation-2026-11-16.json)。

[rag-retrieval-v1.json](datasets/rag-retrieval-v1.json) 用于比较 `BM25`、`DENSE`、`HYBRID`（RRF k=60）、`HYBRID_RERANK` 和 `HYBRID_RERANK_REWRITE`。评测接口仅在隔离评测开关开启时注册，要求管理员身份；复用实际 Milvus 检索、证据判定、查询改写和答案组装代码。每题显式提供商品 ID，答案上下文不包含商品表里的规格，避免绕过检索直接回答。本实验不测路由、商品识别、多轮记忆或订单工具执行。

数据基于 `data/retail-v1.1` 的 11 份公开资料摘要、88 个来源章节。80 题沿用已有知识问答，另外编写 16 道跨两个章节的问题和 8 道资料不足问题，共 104 题。多证据题组合屏幕与存储、有线与无线充电、系统版本与升级等事实；资料不足题覆盖维修报价、寿命、固件校验值、温度、功耗等缺失信息。这是项目内回归与挑战集，不能宣称独立人工标注或真实客服流量。旧 development/holdout 已经用于过调试，本次分别标为 known_development/known_holdout。问题、参考答案、必要事实、来源文件及章节标签在执行前冻结，并核验来源 SHA-256。`prepare_rag_benchmark.py` 的生成过程可复查，已有文件不会被自动覆盖。

所有组使用相同商品和生效文档版本过滤、最多 20 个召回候选、最终 Top 5 及同一个证据判定器。最后两组启用重排，最后一组仅在证据不足时最多改写一次；并非每题都会改写。单商品只有约 8 个来源章节，候选空间较小，不能据此推断大规模知识库效果。

| 指标 | 计算口径 |
| --- | --- |
| Recall@5 | Top 5 命中的不同必要章节数 / 标注必要章节数，再对 96 道可回答题宏平均；同一章节的 SKU 副本不重复得分 |
| Hit@5、MRR@5 | 是否命中任一必要章节、首个正确章节排名倒数；只看前 5，未命中计 0 |
| 严格答案通过率 | 必要来源段落全部选中、未选择标注范围外的文档段落且未错误拒答；资料不足题须返回不足且不选择文档事实 |
| 事实覆盖、标签精确率、拒答 | 另行报告必要事实覆盖比例、所选段落中属于参考事实的比例、8 道无答案题正确拒答率和可回答题错误拒答率 |
| P50 / P95 | 单并发下评测接口的客户端总耗时，包含检索、判定、回答和追踪写入；5 次预热不计入，成功请求与全部已尝试请求分开统计，使用 nearest-rank 分位数，不代表线上 `/chat` 的 P95 |

检索指标读取证据判定前的真实候选排名。改写组使用最后一轮排名，同时保存首轮指标。答案通过率是项目现有“模型选事实编号、服务端输出原文”的契约检查，不是独立人工语义正确率，也不能写成通用幻觉率。标注范围外的补充事实可能仍然相关；首轮报告保留原始计分，另列标签边界问题，不事后修改标签提高分数。没有答案的题不计入召回分母。执行失败和未执行题保留在计划质量分母中；中断报告只能称部分结果。

离线检查：

```powershell
python -B scripts/evaluation/prepare_rag_benchmark.py
python -B -m unittest discover -s scripts/evaluation/tests
```

云执行前需授权公开资料与调用范围：104 × 5 次正式问答、5 次预热，连同首次入库共用最多 2400 次供应商请求。使用已有 `qwen-plus`、`text-embedding-v4`、`qwen3-rerank` 配置；单次聊天输出限制 1024 token。请求数限制不是金额硬上限。供应商失败后停止，不自动重试，也不能通过重启服务重置计数继续跑。

确认授权并提交冻结代码后执行：

```powershell
New-Item -ItemType Directory -Force quality/evaluation/reports/rag-benchmark-traces
docker compose --env-file .env -p serviceflow-rag-bench -f deploy/evaluation-rag-benchmark.yml up -d --build
python -u -B scripts/evaluation/import_retail.py --package data/retail-v1.1 --receipt quality/evaluation/reports/rag-benchmark-import.json --allow-cloud-ingestion --wait-seconds 1200
python -u -B scripts/evaluation/rag_benchmark.py --allow-cloud
```

环境使用独立 MySQL 数据库、Milvus 卷和本机 18080 端口。正式运行以固定种子打乱题序，并轮换五组执行顺序。逐条原始证据、模型选择和计时写入被 Git 忽略的 `reports/rag-benchmark-*/attempts.raw.jsonl`；`summary.json`、`report.md` 保存总体与分题型指标、失败数、源码提交、资料哈希、镜像和模型配置。公开结果前仍需检查脱敏，不发布原始模型响应或凭据。

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

[离线诊断验证](results/rag-diagnostics-validation-2026-10-25.md)区分工程模拟和云结果；此前3轮/27次计划已按授权完成[三例诊断](results/rag-cloud-diagnostics-2026-10-25.md)。随后[多轮商品范围及自然第二轮诊断](results/rag-context-followup-2026-10-25.md)在固定7轮内使用31/63次provider尝试，引用实际分块逐条裁决。原12/14与已见1/2保留，最新开发诊断不替代首次留出。

旧200集的197/200是修正标签后的离线规则重评分；所谓Recall@5 97.69%实际按最终引用文件名计算，分母为130条要求引用题，命中127条。没有该批次召回候选排名、运行源码或模型配置冻结，不能把它当作当前chunk召回率、语义准确率或独立留出。[数字溯源](../../docs/portfolio/resume-project.md#数字怎么使用)保留生成时间、产物提交与适用条件，历史实验文件不改。
