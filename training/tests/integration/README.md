# 固定 Trainer 集成检查

这些脚本使用真实 PyTorch / Transformers CPU 训练，验证恢复兼容修正和 checkpoint 保存策略。它们需要已经固定版本的训练环境，不属于普通 `unittest discover -s training/tests` 的轻量测试。

从仓库根目录、使用固定训练环境 Python 执行；`--out` 必须是尚不存在的新目录：

```bash
python -B training/tests/integration/test_epoch_checkpoint_policy.py --out runtime-data/training/check-epoch-policy-new
python -B training/tests/integration/test_trainer_resume_compat.py --epoch-fix --out runtime-data/training/check-resume-new
```

第一个检查每 epoch 保存、完整封存后轮换及恢复一致性；第二个复现尾批问题并检查修复后的权重一致性。仅在更改保存/恢复机制或固定依赖时需要重跑，不在目录整理时重新训练。
