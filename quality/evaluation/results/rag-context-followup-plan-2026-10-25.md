# 已核限定范围：商品范围与自然两轮RAG真实模型方案

**总控已按用户既有真实实验授权核准本固定7轮/63请求范围；尚未执行。** 复用项目 `.env` 的 `qwen-plus` / `text-embedding-v4` / `qwen3-rerank`，不更换模型、不新增语料、评审模型调用、后台入库或探测。实现修复基线为 `a533debd`；调用前冻结最终通过CI的源码、数据、suite及镜像指纹和活动版本，不把当前未执行方案称为结果。

[可执行suite](../datasets/rag-context-followup-v1.json)包含4个开发场景、7个新聊天回合、2段多轮；[机器可读计划](rag-context-followup-plan-2026-10-25.json)固定问题、阶段要求和预算。原Apple/Samsung来源已暴露，所有措辞均为开发诊断，不能作为独立留出。

| 场景 | 回合/重复 | 检查与来源 |
| --- | --- | --- |
| seen-display-repeat | 单轮1次 | 原屏幕失败问题，Apple display段落；保留标准矩形和实际较小可视区限定 |
| explicit-switch-pronoun | 连续3轮1次 | 首轮再次使用同一原屏幕问题（另一新会话，合计两次初始采样）→明确SKU切换Samsung→代词追问。后两轮只允许Samsung商品和display来源 |
| unknown-sku-no-fallback | 连续2轮1次 | Apple入口上明确未知SKU→“它”的追问；两轮均不得回退旧商品事实、引用或RAG，要求澄清商品 |
| natural-rewrite-partial | 单轮1次 | 同时问屏幕错误前提和每个圆角损失多少平方毫米。来源能纠正前提，但没有面积数字；要求正确部分回答及明确剩余资料不足，用于观察自然第二轮 |

旧屏幕题两次采样不估计长期稳定率；同一问题分别在两个全新会话的首轮出现，未按结果选择顺序或补跑。运行单并发，所有7个槽位（失败或未尝试也包括）保留在分母。

## 出站与执行边界

单轮最坏：意图1 + embedding2 + rerank2 + Grader2 + rewrite1 + 事实选择1 = **9次provider尝试**。7轮硬上限 **63次**，失败也扣次数；无重试、服务重启、扩容、其他聊天或自动补跑。未知SKU正常只需要分类，但预算保守计入误路由可能触发的完整路径。63不是token或金额上限，未知用量/费用记录null。

使用既有共享内存计数器，只加显式[63次overlay](../../../deploy/evaluation-rag-followup.yml)，不改变默认27次配置。真实客户端+loopback HTTP测试已检查27和63上限、失败计数和第limit+1次发送前阻断；SDK/AOP/JDK保持单次。该测试不产生远程模型调用。若重启、活动版本变化、限额异常或计数无法核对，停止并保留结果，不新建进程补额度。

本overlay另启用 `stop-on-provider-failure`：在同一个预算对象中锁存HTTP错误、同步body IO、流式body错误/缺少DONE及应用deadline超时；后续准入全部拒绝，普通模式的降级策略不改。固定串行执行器复用现有run_case；每次API前后核对预算和进程身份，遇到外部故障立即保留实际响应并将剩余槽位记未开始。语义回答或规则失败本身可继续原顺序。应用deadline夹具没有wire请求；本地429/断连/body IO才是实际HTTP故障夹具，不能混称真实网络超时或穷尽所有断流协议。

原问题/商品ID/活动版本、每轮实际检索query和候选、重排/Grader保留及未选择、改写与第二轮记录使用现有trace。最终证据选择响应未单独记录的原始部分不补造；用返回正文、当前目录事实和所有实际chunk正文逐项建立事实/引用对应。现有字段足以核验固定范围，无需新观测平台。

## 裁决与二轮覆盖门槛

规则检查、阶段覆盖、逐项语义裁决分开报告。每轮保存原问题、预期/实际SKU、来源与完整限定、实际回答、每阶段排名及失败类别，检查是否回应问题、是否误肯定前提、是否混入上轮商品或未经来源支持的数字。未知SKU不得因“保守措辞”而夹带旧参数；部分面积题不得编造平方毫米。

仅当真实trace含rewrite与rewritten.retrieval/rerank/grader，才记自然两轮已观察。**未触发则写not_observed，不能把规则通过当作第二轮闭环**；不强制篡改Grader、不加题或持续重跑凑覆盖。原始Grader返回理由未知时只陈述验证后的决定，不捏造拒绝原因。

人工或代理按当前目录事实和实际来源正文逐条裁决，记录裁决者及未完成项；若没有独立人类标签，就标代理辅助/待人工，不发布人工语义准确率。单独报告各阶段语义失败、来源不支撑、错误商品、未知数值和coverage缺失；模型可用性错误不能从分母删除。

以下5步必须有trace：seen-display-repeat:0、explicit-switch-pronoun:0/1/2、natural-rewrite-partial:0。未知SKU两步期望无RAG事件；若意外出现也保留并判该边界失败。采集全部中间候选，不只最终引用。

## 本限定授权内的执行命令

```powershell
$docker = 'D:\Applications\Docker\resources\bin\docker.exe'
# 先构建与通过CI一致的代码；确保Shell的OPENAI_*不会覆盖项目.env。
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml build server
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml stop
$cloud = @('--env-file','.env','-p','serviceflow-cloud-eval','-f','deploy/evaluation-cloud-compose.yml','-f','deploy/evaluation-provider-budget.yml','-f','deploy/evaluation-rag-followup.yml')
& $docker compose @cloud config --quiet
& $docker compose @cloud up -d --no-build --wait --wait-timeout 300
# 按既有预算手册只读保存模型名、活动版本、Git/数据指纹、容器ID/StartedAt/RestartCount，确认used=0。
python -u -B scripts/evaluation/run_rag_followup.py --docker $docker --allow-cloud
# $run为此次RUN目录中的responses.raw.json；不得指向历史运行。
python -B scripts/evaluation/capture_evidence.py --run $run --docker $docker --trace-directory quality/evaluation/reports/rag-traces --require-trace-steps seen-display-repeat:0 explicit-switch-pronoun:0 explicit-switch-pronoun:1 explicit-switch-pronoun:2 natural-rewrite-partial:0
& $docker compose @cloud stop
& $docker compose -p serviceflow-eval -f deploy/evaluation-compose.yml up -d --no-build --wait --wait-timeout 180
```

运行元数据保存方式复用[既有预算手册](provider-budget-validation-2026-10-25.md)；发布本方案时只静默校验合并配置，等待最终实施提交CI成功后才启动Cloud。执行器对HEAD检查暂存/未暂存变更，必需suite/plan/collector必须已跟踪并参与指纹；无关.publish保留且不参与模型输入。旧12/14、1/2、RAG3/3、业务6/6原始成绩保持不变，不用新开发结果覆盖历史失败。
