# 本地试验的 AI 审核授权

2026-09-15 用户明确指示：“审核由你完成  然后进行sft”。

据此，本次本地小规模 SFT 允许 Codex 审核。记录使用 `review_status=ai_approved`、`reviewer_kind=ai`、`heldout_review_kind=ai`，逐条保存依据；不伪称人工审核。原人工审核流程和原始草稿保持原样。

授权范围是本地数据整理、短程/一轮 LoRA SFT 与本地验证，不代表线上切换、业务权限验收或生产质量保证。保留 200 条评测及派生题禁止训练；合成事实不得进入生产知识库。

## 官方资料业务候选

用户随后要求“帮我进行正式训练和接入”，并要求“帮我搜索更多适合实际场景的资料”。据此继续搜索公开官方资料、逐条 AI 审核、训练新的业务候选，并在本机隔离环境接入和验证。

`official-business-v1` 保存 `scope=business_candidate_sft_local_integration`，同时记录上述实施指令。此范围没有把 AI 审核改称人工审核，也没有授权生产切换或额外付费云评测。新资料来源、事实适用范围和数据拆分见 [数据报告](../reports/official-business-v1-data.md)。

## v2 完善与保存策略

2026-09-16 用户要求：“可以帮我进行完善，同时取消之前每步都保存checkpoint，改为每个epoch保存最新checkpoint”。继续在既有授权范围内审核基于原训练来源的新场景，进行独立一轮 v2 SFT、验证选择和本地隔离接入。新实验每个 epoch 保存完整状态且仅保留最新一份，历史数据和 checkpoint 原样保留。结果不佳时保留 v1 候选，不将完成训练等同于质量提升。详见 [v2 记录](../reports/official-business-v2.md)。
