# ServiceFlow 待优化项

## AI 评测优化：区分检索质量与回答质量

- [ ] 保留意图、商品 ID、事件等硬规则；明确 requiredFacts 的任意/全部命中语义，补充否定句、同义表达和错误事实反例，避免把关键词命中当成语义正确。
- [ ] 优先保存完整回答、问题、检索分块（chunkId、文档版本、内容、排序与分数）及最终生成上下文，不再仅靠前 240 字摘要复核；脱敏保存并限制访问。
- [ ] 标注相关分块，分别评估召回与重排序阶段的 Recall@K、MRR、nDCG@K。现有按引用文件名计算的指标标注为文档引用层指标，不等同于分块检索指标。
- [ ] 增加基于问题、证据和完整回答的模型评审，分别评价证据忠实度、回答正确性与完整性；正确性结合人工标注的预期事实，不能仅凭检索证据自证正确。
- [ ] 人工抽查和复核争议样本，校准评审模型；禁止短语命中率仅作为幻觉代理指标，不宣称为完整幻觉率。
- [ ] 固定并记录测试集、知识库版本、提示词、各阶段模型和检索参数；对比基线时报告分项指标、失败案例、耗时、成本，必要时重复运行观察波动。
- 验收目标：能够区分路由错误、证据未召回、排序不佳和生成错误；包含正确关键词但意思相反的答案不能仅靠关键词获得质量通过判定。
- 相关代码：
  - [在线请求与规则判分](scripts/evaluation/run-evaluation.ps1:61)
  - [评测数据集](quality/evaluation/datasets/serviceflow-eval-200.json:1)
  - [离线审计与引用层指标](scripts/evaluation/audit-evaluation-report.ps1:62)

## 意图路由与商品别名解析

- 当前问题：`CustomerWorkflow.routeIntent()` 先依赖 AI 分类和固定正则。订单号可能被 `MODEL` 正则误识别为商品型号；商品口语化别名（例如“苹果16p”“苹果十六 Pro”）也可能无法命中固定字符串规则。
- 相关代码：
  - [CustomerWorkflow.java:49](apps/serviceflow-server/src/main/java/com/serviceflow/agent/CustomerWorkflow.java:49)
  - [CustomerWorkflow.java:209](apps/serviceflow-server/src/main/java/com/serviceflow/agent/CustomerWorkflow.java:209)
  - [ProductMapper.xml:25](apps/serviceflow-server/src/main/resources/mapper/ProductMapper.xml:25)
- 优化方向：先识别订单号、SKU、政策编号等高确定性标识；再进行商品别名规范化和实体解析；最后结合 AI 判断自然语言意图。
- 可选方案：增加商品别名表、输入规范化、别名精确匹配与语义检索，并补充订单号/商品别名/页面上下文的路由测试。
- 验收目标：订单号不会被路由为商品查询；常见口语化商品名称能够解析到正确的 `productId`；规则失败时仍有可控的 AI 降级路径。
