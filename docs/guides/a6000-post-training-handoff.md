# A6000 Grader SFT 交接

更新日期：2026-09-17。当前入口为 [训练首页](../../training/README.md)，所有维护命令通过 `training/cli.py` 执行。

## 最终交付

唯一保留的最终 SFT 候选是 **Qwen3-8B，Grader 专用，seed 42 / epoch 2**。主种子在训练前预登记，epoch 由验证集选择；不是按测试结果挑选种子。BF16 LoRA，rank 16 / alpha 32，单张 RTX A6000 48GB。训练、来源、数据和验收参数见 [预登记与数据卡](../../training/reports/grader-specialist-exp-01.md)。

180 条新来源测试中，原始模型综合正确率 58.9%，主候选 100%，复现实验 seed 2026 为 98.9%。旧压力测试仍显示复杂适用范围推理的不足。数据由 AI 编写和审核，无独立人工复核，来源隔离不等于模板完全不同。详细结果和限制见 [最终报告](../../training/reports/grader-specialist-exp-01-results.md)。

## 模型边界

该模型只接收 query + candidates，输出 sufficient + rankedChunkIds。它不是全任务聊天模型，也不负责订单操作、权限、二次确认、幂等或退款承诺。真实 Java Grader 调用 5/5 验收通过。旧业务 v1 服务已停止；未自动将最终模型接到所有客服请求。

## 代码与本地产物

代码按数据、训练、评测、环境、运行及公共模块分组。旧 pilot/v1/v2 编排入口已移除。

Git 只保存代码、来源计划、标签规则、配置模板和可公开报告。权重、完整数据、网页快照、预测及密钥均不上传。新机器需要单独取得或重建本地产物，不能把克隆仓库当作获得已训练模型。

最终身份见 [配置](../../training/configs/final-model.json)。保留主模型、基座、主种子最终 adapter 和完整 checkpoint，另保留双种子评测及验收凭据；其他旧权重及失败日志已清理。

## 核验和恢复

```powershell
python -B training/cli.py status
python -B training/cli.py resume-final --verify-state
python -B -m unittest discover -s training/tests -v
```

模型状态命令是产物检查，不表示服务已启动。完整 checkpoint 绑定原训练代码哈希，因此最终恢复使用封存的 8 文件引擎附件；新实验使用整理后的引擎。参见 [恢复指南](../../training/checkpoint-guide.md)。

训练/推理使用固定的 Linux/WSL 环境。下载优先官方国内可核验入口，记录实际 revision 和哈希。不得将旧评测题加入训练，不得根据模型预测修改冻结标签；继续实验应使用新目录和新版本。当前不需要重跑已完成的双种子实验。
