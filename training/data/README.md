# 审核样本契约 v1

这里只放说明和测试代码使用的合成结构，不提供冒充人工审核的训练数据。真实记录放 `local-datasets/serviceflow/`，模型、导出与预测放 `runtime-data/training/`，均已被仓库忽略。

## JSONL 每行字段

| 字段 | 含义 |
| --- | --- |
| `id` | 全局唯一的审核样本 ID |
| `task_type` | `intent / grader / rewrite / product_answer / chat` |
| `source_id` | 原始会话或原始 QA 样本 ID；不是整个公开数据集 ID |
| `group_id` | 划分集合前确定的同源组 |
| `template_family` | 生成/改写模板族 ID；同族必须在同一集合，不用通用任务名代替 |
| `split` | `train / validation / test`，人工先分组再标定 |
| `source_uri / source_revision / license` | 原始来源、固定版本和许可记录路径/标识；公开数据必须保留下载证明 |
| `synthetic` | 布尔值，是否含虚构事实；合成商品不得导入真实知识库 |
| `review_status / reviewer / reviewed_at` | 导出只接受 approved 和真实审核人/时间，不自动给样本盖审核章 |
| `heldout_derivative_reviewed` | 人工确认不是既有 200 条评测的翻译/改写/派生样本，必须为 true |
| `messages` | 恰好 system/user/assistant 三条，content 是实际序列化文本 |
| `context` | 保存当前原始问题与任务需要的 candidates/history |
| `snapshot` | 快照 version；商品任务另存 facts_version/evidence_version/grounding_context |
| `checks` | 可选 required_phrases/forbidden_phrases，逐项匹配只是代理指标 |

基础结构示意（draft 不能导出）：

```json
{
  "id": "intent-original-001",
  "task_type": "intent",
  "source_id": "original-conversation-001",
  "group_id": "original-family-001",
  "template_family": "author-created-family-001",
  "split": "train",
  "source_uri": "project-authored://serviceflow/original-conversation-001",
  "source_revision": "draft-v1",
  "license": "待审核并保存来源许可",
  "synthetic": true,
  "review_status": "draft",
  "reviewer": "",
  "reviewed_at": "",
  "heldout_derivative_reviewed": false,
  "messages": [
    {"role": "system", "content": "用 pipeline.py prompts 输出的当前 intent 提示词原文"},
    {"role": "user", "content": "请帮我查查这个订单是否已经打包。"},
    {"role": "assistant", "content": "ORDER_QUERY"}
  ],
  "context": {"question": "请帮我查查这个订单是否已经打包。"},
  "snapshot": {"version": "scenario-draft-v1"},
  "checks": {}
}
```

## 任务输入

意图、改写、普通聊天的 user **必须等于当前问题**，不额外注入历史。意图输出只能为五个枚举之一，改写为单行。

Grader 的 `context.candidates` 是 `{chunkId,title,content}` 数组；user 是 `{"query": question, "candidates": [...]}` 的实际 JSON 字符串，保存原始字段顺序。校验比较 JSON 结构，不强制重排键，以兼容 Java Map 序列化。目标只能有 `sufficient` 布尔值和 `rankedChunkIds` 字符串数组，最多五条、去重、属于候选，充分时不能为空。相关但条件不满足的文本应标为不充分，不能仅靠关键词标注。

这里的“条件不满足”指证据不能支持所问事实；如果问题本身是“是否适用”，明确的适用范围限制可能足以回答否定结果。按 [标注规则](grader-labeling-rubric.md) 分别判断充分性和支持 ID；不充分时允许非空 ID。

商品回答保留 `context.history` 最近最多八条 `{role: USER/ASSISTANT, content}`。Java 会先把本轮 USER 放入 Redis，因此历史末条包含当前问题。`snapshot.grounding_context` 原样保存 `结构化商品事实：\n...\n\n文档证据：\n...`。完整 user 必须为：

```text
会话上下文：
USER：历史问题或本轮问题
ASSISTANT：历史回答（如果存在）
USER：本轮问题（历史末条）

当前用户问题：
本轮问题

可信商品上下文：
结构化商品事实：
实际 MySQL 事实 JSON

文档证据：
实际证据原文与来源
```

上面是结构说明，历史内容不要重复机械插入。以 Java 实际拼接值为准。快照可包含旧文档冲突、型号干扰、恶意指令；这些只作为不可信证据，不进入 system。审核目标不得生成“已取消订单/已退款”等虚构操作结果。

## 隔离与不可自动保证的内容

校验器拒绝未审核、ID 重复、同源或同模板族跨集合、同任务重复输入、已知保留题 ID，以及 train/validation 输入中嵌入的规范化保留问题。不会自动重新分组，也不从历史评测生成样本。

语义改写、跨语言翻译、源 ID 伪造、目标事实真实性、许可是否足够，不能仅凭格式检查可靠判断；必须人工审核 lineage，并保留实际事实/证据和来源记录。`heldout_derivative_reviewed` 是审核声明，不是机器证明。每次导出记录 Java 文件 SHA、保留评测 SHA、审核数据 SHA 和导出文件 SHA，来源变更后必须重验。

首批训练目标 1000 条，验证/测试另建，不包含在 1000 条里。首批配比和扩充到 5000 条的规划见交接文档。

## pilot-v1：先逐条审核的 100 条批次

已建立 `local-datasets/serviceflow/pilot-v1/`：100 条训练候选（20 意图、30 Grader、10 改写、40 商品回答），另有 25 条验证候选、100 条独立模块评测候选。全部是 `draft`，没有自动批准记录，不等于计划的 1000 条审核训练样本已完成。

40 条训练商品回答引用仓库迁移与文档历史快照，**不是实时 MySQL 数据**；其余输入/证据情景为合成案例。验证与模块评测采用独立情景和模拟事实，不进入真实知识库。Bitext/SQuAD 原始数据尚未直接用于此批次。

- 审核入口：该目录中的 `REVIEW_GUIDE.md`；三个 `*-review.md` 提供真实输入、来源和候选答案，`*-decisions.jsonl` 等待人工填写。
- `pipeline.py inspect --input ...` 检查草稿的格式、契约和显式泄漏，不代表审核通过。
- `inspect_draft_lengths.py` 只检查草稿 token 长度。审核后仍须运行正式 `validate/export/lengths`。
- `apply_reviews.py` 只应用明确的人工作出的决定，核对草稿 SHA，保留修订与排除记录；不训练、不导出、不自动提升实验锁。
- 新增 `heldout-registry.json` 保护独立模块评测：训练/验证不得复用其来源、模板族或规范化问题。注册表哈希随输入契约冻结，变更会使旧导出/测长记录失效。
- 固定基线不能删除难例，不能仅用已审核的部分计算指标。模型运行前冻结参考版本；模型输出与基线人工审核材料分开保存，标签修订必须有证据和版本记录。

## 数据与实验卡应记录

2026-09-15 后续预审已生成独立修订草稿，见 [预审报告](../reports/pilot-preaudit-v1.md)。用户确认 [Grader 部分支持规则](grader-labeling-rubric.md)：不充分时仍保留支持问题一部分的 ID。原基线保持不变，逐题人工审核尚未完成。

### 用户授权 AI 审核后的本地试验

用户随后明确要求“审核由你完成  然后进行sft”，见 [授权记录](ai-review-authorization.md)。本地试验数据使用 `ai_approved`，保留 AI 身份、逐条依据与来源审查，不填写虚假的人工审核记录。`pipeline.py prepare` 对这种数据要求实验锁明确声明 `review_scope=ai-reviewed-local-pilot`。

`local-datasets/serviceflow/ai-reviewed-pilot-v1/` 是新版本：原 100 条训练和 25 条验证候选整体隔离，避免与保留题的来源/模板近似。新建 78 条合成训练和 10 条验证；测试保留完整 100 条输入，仅另存有记录的参考标签修订。模拟事实不进入生产知识库，也不冒充实时商品资料。该批仅有 1,166 个训练目标 token，是小规模 SFT 试验，不是完整 1000 条业务数据集。

- 原始国内/国外来源、实际国内下载入口、revision、SHA-256、子集、许可证及获取日期。
- 原始样本与模板族映射、train/validation/test 划分时间、审核人、纠错记录。
- 各任务样本数、事实/证据版本、拒答/难例类别、token 长度与超长处理记录。
- 基线与 SFT 的同一测试集、同一知识快照、同一解码配置和人工评分标准。
