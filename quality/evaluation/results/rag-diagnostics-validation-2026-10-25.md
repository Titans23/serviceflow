# RAG阶段诊断与原问题判据回归（离线）

基线 `616e92e5` 的[CI #9已成功](https://github.com/Titans23/serviceflow/actions/runs/40166337544)。本检查点没有新云模型请求、没有新增商品语料，也没有更改原留出标签。首次12/14及已见回归1/2保持不变。

## 先证明再修复

使用已保存的 `fresh-iphone-display` 问题和iPhone16资料中的屏幕/亮度段落，配合确定性网关响应与本机HTTP stub，先在未修复实现上得到2/2失败：[修复前摘要](rag-diagnostics-before-2026-10-25.summary.json)。

1. 改写故意漂移到亮度问题，亮度证据可以回答改写问题，却不能回答原始圆角可视面积问题。旧代码用改写问题调用第二轮Grader，错误宣告充分；修复后改写仅用于召回/重排，Grader始终判断原用户问题。
2. 首轮Grader失败后，第二轮成功会抹掉首轮degraded。修复后汇总整个检索过程的降级状态，成功补救不等于从未失败。

这里的改写漂移、接受/拒绝与故障响应是**测试构造**，不是抓取到的原云模型响应。它证明实现违反了这两个约束，不证明它们就是此前屏幕题的真实根因。也没有据此宣称屏幕题端到端已修复。

## 记录与收集契约

只有 `SERVICEFLOW_EVALUATION_ENABLED=true` 且设置诊断目录时，才写入本地 `<traceId>.raw.json`。Cloud评测profile将目录映射到Git忽略的 `quality/evaluation/reports/rag-traces/`；默认普通服务及未配置目录的Demo不写。只在这种评测路径增加 `rag_diagnostic` 关联ID事件，不在客服界面显示阶段实现细节。

阶段记录包含：原问题、实际用于该阶段的查询及SHA、活动版本/商品ID、候选ID与排名、保留/未选择的机制原因码、充分性（仅Grader）、降级与耗时，以及最终证据。正文不写进阶段文件，通过既有Milvus证据采集另存本机 `evidence.raw.json`。原始查询/改写也是本机评测数据，不进入公共摘要。

范围是混合召回的RRF融合列表→重排→Grader→改写→第二次召回/重排/Grader。没有分别获取Dense/BM25内部列表；“召回耗时”包含embedding及Milvus请求。拒绝原因码说明未被Grader返回、空输入、重排/Grader/改写异常等机制，**不是模型未提供的逐块自然语言理由**。集合初始化之前或方法入口外的熔断不包含在这些阶段记录中。

采集器可以保留最终输出中已消失的中间候选，不能将“有至少一份trace”称为整轮完整。拟议3例明确要求以下逐步覆盖：

```powershell
python -B scripts/evaluation/capture_evidence.py --run $run --docker $docker --trace-directory quality/evaluation/reports/rag-traces --require-trace-steps seen-screen:0 unseen-screen-wording:0 unseen-brightness-control:0
```

缺少任一指定步骤、文件或关联ID时保存 `diagnostic-coverage.json` 的incomplete状态并失败；普通未要求RAG的步骤可以没有trace。没有诊断ID、写盘失败或只剩空最终引用时不补造记录；空召回本身仍可有完整阶段诊断。

## 验证

- 8个新RAG离线测试通过：原问题判据、累计降级、模拟屏幕假阴性的阶段定位、反证接受路径、空召回、重排降级、改写失败、评测开关/目录/写盘失败边界。
- 相关Java定向22项通过；完整后端单元114项中109通过、5个可选本地Grader跳过，0失败/错误；格式/打包/覆盖率门禁通过。
- Python工具32/32，含中间候选保留、显式逐例覆盖缺失、错误关联/路径形ID、空召回记录。
- Docker后端构建与Demo启动通过，真实Demo HTTP冒烟确认有done且无诊断事件；未启动Cloud模型评测。Compose配置验证确认保留原uploads卷并另加本地诊断挂载。

结果：[修复后摘要](rag-diagnostics-after-2026-10-25.summary.json)。复现命令（无收费调用）：

```powershell
Push-Location apps/serviceflow-server
mvn -DskipITs -Dtest=RagRewriteDiagnosticsTest,CloudEvidenceSafetyTest,MilvusCollectionInitializationTest,ProductQuestionRoutingTest test
Pop-Location
python -B -m unittest discover -s scripts/evaluation/tests
```

## 待授权的云验证计划

[计划文件](rag-cloud-validation-plan-2026-10-25.json)包含1个已见问题及2个未见措辞，3轮、单并发，不重新入库。拟议provider出站上限27次：意图3、embedding6、重排6、Grader6、改写3、事实选择3；失败尝试也占预算，不额外调用复核模型。

当前默认Spring AI与Resilience4j均可能重试；计划要求关闭相关重试并在第28次出站前拒绝。后续[出站预算离线检查点](provider-budget-validation-2026-10-25.md)已提供隔离启动配置和真实客户端HTTP stub证明；`max-chat-turns`本身仍不是provider请求上限。实际执行仍需具体数据/费用授权。这个离线检查点不改变历史云分数，也不产生新的独立语义准确率。
