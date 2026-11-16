# RAG v2：中文分词与评测标签修订

本轮保留 `rag-retrieval-v1.json`、2026-11-16 首轮报告和本机原始记录，新增 [rag-retrieval-v2.json](datasets/rag-retrieval-v2.json)。不修改简历。新题和标签在首次模型执行前提交冻结；新题结果不用于本轮参数、提示词或标签修改。

## 变更与数据边界

- 新建 Milvus 集合使用内置 `chinese` analyzer（Jieba）。本地 Milvus v2.6.22 已验证中文切词、REST schema 写入及短语 BM25 命中；保留数值、型号、接口名称的探针结果也已检查。
- 现有 `standard` 集合不会被就地删除或修改。新代码检查已有集合配置，遇到不匹配时要求显式重建，避免只改 Java 声明却仍使用旧稀疏向量。初始化校验完成后才设置就绪状态，并串行化该过程。
- v2 在独立项目 `serviceflow-rag-v2`、新 MySQL 数据库和新 Milvus 卷运行。旧 `serviceflow-rag-bench` 环境的数据保留。
- 修订 104 道已知回归题：13 条澄清措辞；基于来源允许防水实验与防护限制、系统版本与升级、充电配套与功率等相关补充。每题完整划分必需、允许补充、禁止混入的同商品来源章节，并绑定原文事实。历史问题、来源哈希和 v1 数据集哈希可追溯。
- 新增 40 条问题：19 条单证据、11 条多证据、10 条无答案，覆盖错误前提、自然问法、多个约束和缺失信息。它们与旧题文本不重合，尚未用于参数或提示词调整；来源仍是已知的 `retail-v1.1`。这是**新问题留出**，不是独立人工标注或新来源留出。
- 语料保持不变，以便区分分词变化。每题约 8 个章节的局限仍在，新增问题不等于扩大知识库；不承诺本轮能证明大规模检索泛化。

## 计分规则

检索按必需章节计算 Recall@5、Hit@5、MRR@5。允许补充的章节不会扩大必需证据分母；SKU 副本不重复得分。126 道可回答题参与检索宏平均（回归 96，新题 30），18 道无答案题单独评估拒答。

答案通过条件为：必需事实全部选中，额外文档事实属于事先标注的允许范围，没有选入禁止事实，且不足标记正确。仅选可选事实不能补偿必需事实遗漏。输出同时保留严格只选必需事实的通过率、允许事实数量、禁止事实数量和 Grader 后上下文对必需事实的覆盖，便于区分召回、筛选和答案选择问题。

v1 没有 `labelVersion=2`，继续使用原规则；已经用新评分模块重新计算旧 525 条原始记录及总表，全部与首轮结果一致。v2 问题及标签已有变更，**v1/v2 的答案通过率不能直接相减当作模型质量提升**。回归与新问题成绩分别报告，不能用合并成绩替代新题结果。

这仍然是来源事实选择与拒答的自动规则检查，不是独立人工语义正确率。标签不允许所有同主题内容无条件通过，保持禁止混入的范围。旧版错误分析可用于修订回归标签，但新题不得在看过模型输出后补标签提高成绩。

## 两层对照

1. **云端四组完整问答**：中文 BM25、纯向量、中文混合（RRF k=60）、中文混合 + Rerank。相同商品/版本过滤、候选上限 20、最终 Top 5、Grader、生成提示词、模型及温度；本轮不改这些参数。每题每组一次，共 144 × 4 = 576 次正式问答，加每组一次回归题预热，共 580 次。首轮未验证到改写召回收益，本轮不加入改写组。
2. **本地分词配对对照**：对冻结的同一批 144 题，分别查询旧 standard 集合和新 chinese 集合的 BM25 Top 5，不调用 Embedding、Grader、Reranker 或生成模型。先按 SKU 核验两边章节原文完全相同，再比较 126 道可回答题的召回和排序；无答案题不参与召回分母。只读旧集合，不改旧成绩。这一层可直接观察分词变化，避免把问题/标签变化混入收益。

模型仍为 `qwen-plus`、`text-embedding-v4`、`qwen3-rerank`，温度 0.1，单次输出上限 1024 token。新进程统一限制 2400 次供应商请求，首次入库预计 192 次，四组正式问答最多 1728 次，预热最多 12 次，合计预计不超过 1932 次；2400 是防止越界的硬限制，不是账单金额上限。收费执行前确认本轮数据与成本范围。供应商错误立即停止，不自动重跑、重启计数或挑选较好结果。

## 执行

离线检查：

```powershell
python -B scripts/evaluation/prepare_rag_benchmark_v2.py
python -B -m unittest discover -s scripts/evaluation/tests
# apps/serviceflow-server 下执行 mvn -DskipITs verify；MySQL 集成门禁由 CI 另行报告。
```

本轮授权确认、代码与标签提交后：

```powershell
New-Item -ItemType Directory -Force quality/evaluation/reports/rag-v2-traces
docker compose --env-file .env -p serviceflow-rag-v2 -f deploy/evaluation-rag-v2.yml up -d --build
python -X utf8 -u -B scripts/evaluation/import_retail.py --package data/retail-v1.1 --receipt quality/evaluation/reports/rag-v2-import.json --allow-cloud-ingestion --wait-seconds 1200
python -X utf8 -u -B scripts/evaluation/rag_benchmark.py --suite quality/evaluation/datasets/rag-retrieval-v2.json --server serviceflow-rag-v2-server-1 --milvus serviceflow-rag-v2-milvus-1 --trace-dir quality/evaluation/reports/rag-v2-traces --allow-cloud
# 只启动旧 Milvus，绝不启动旧云模型服务来重置原计数。
docker start serviceflow-rag-bench-milvus-1
# 待旧 Milvus 健康；输出路径必须全新。
python -X utf8 -B scripts/evaluation/compare_bm25_analyzers.py --output quality/evaluation/reports/rag-v2-bm25-paired.json
```

全部结果保留首轮值，发生中断则标为部分结果。输出源码提交、数据哈希、模型别名、运行环境、完整/失败/未执行数、分组指标、P95 和实际请求数。原始模型响应保留在 Git 忽略目录，公开报告只发布指标和必要诊断。本轮每题每配置一次，不据单次结果声称统计显著性。

## 已有云环境迁移

本次不部署到生产环境。主 Compose 透传 `MILVUS_COLLECTION_NAME`，便于显式选择新集合；仅改变量不会迁移已有文档。已有云环境使用旧 standard 集合时，新代码会提示配置不匹配：需保留旧集合，选择新名称，按既有文档入库流程重建并验证生效版本，再安排流量切换。该步骤不提供自动双写、自动回填或零停机迁移。Demo 模式不依赖此集合检查。

参考：[Milvus 2.6 中文 analyzer](https://milvus.io/docs/v2.6.x/chinese-analyzer.md)。
